package com.scanales.eventflow.service;

import com.scanales.eventflow.model.CommunityMember;
import com.scanales.eventflow.model.UserProfile;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CommunityBoardService {

  private static final int DEFAULT_LIMIT = 40;
  private static final int MAX_LIMIT = 200;

  @Inject UserProfileService userProfileService;
  @Inject CommunityService communityService;

  @ConfigProperty(name = "community.board.cache-ttl", defaultValue = "PT1H")
  Duration cacheTtl;

  private final AtomicReference<Snapshot> cache = new AtomicReference<>(Snapshot.empty());

  @PostConstruct
  void init() {
    refresh();
  }

  @Scheduled(every = "{community.board.refresh-interval:30m}")
  void scheduledRefresh() {
    refresh();
  }

  public BoardSummary summary() {
    Snapshot snapshot = snapshot();
    return new BoardSummary(
        snapshot.homedirUsers().size(),
        snapshot.githubUsers().size(),
        snapshot.discordUsers().size(),
        snapshot.loadedAt(),
        false);
  }

  public Optional<BoardGroupPage> groupPage(
      String groupKey, String query, Integer limitParam, Integer offsetParam) {
    Snapshot snapshot = snapshot();
    GroupMeta meta = GroupMeta.fromKey(groupKey);
    if (meta == null) {
      return Optional.empty();
    }

    List<BoardMember> source =
        switch (meta.key()) {
          case "homedir-users" -> snapshot.homedirUsers();
          case "github-users" -> snapshot.githubUsers();
          case "discord-users" -> snapshot.discordUsers();
          default -> List.of();
        };

    String normalizedQuery = normalizeText(query);
    List<BoardMember> filtered =
        source.stream()
            .filter(member -> matchesQuery(member, normalizedQuery))
            .sorted(Comparator.comparing(member -> member.displayName().toLowerCase(Locale.ROOT)))
            .toList();

    int limit = normalizeLimit(limitParam);
    int offset = Math.max(0, offsetParam == null ? 0 : offsetParam);
    int end = Math.min(filtered.size(), offset + limit);
    List<BoardMember> page = offset >= filtered.size() ? List.of() : filtered.subList(offset, end);

    return Optional.of(
        new BoardGroupPage(
            meta.key(),
            meta.title(),
            meta.description(),
            source.size(),
            filtered.size(),
            page,
            query == null ? "" : query.trim(),
            limit,
            offset,
            meta.key().equals("discord-users"),
            snapshot.loadedAt()));
  }

  public Optional<BoardMember> findByRef(String ref) {
    if (ref == null || ref.isBlank()) {
      return Optional.empty();
    }
    Snapshot snapshot = snapshot();
    return Optional.ofNullable(snapshot.byRef().get(ref.trim().toLowerCase(Locale.ROOT)));
  }

  public Optional<GroupMeta> groupMeta(String groupKey) {
    return Optional.ofNullable(GroupMeta.fromKey(groupKey));
  }

  private synchronized Snapshot snapshot() {
    Snapshot current = cache.get();
    if (current.loadedAt() != null && Instant.now().isBefore(current.loadedAt().plus(cacheTtl))) {
      return current;
    }
    return refresh();
  }

  private synchronized Snapshot refresh() {
    List<BoardMember> homedirUsers = buildHomedirUsers();
    List<BoardMember> githubUsers = buildGithubUsers();
    List<BoardMember> discordUsers = List.of();

    Map<String, BoardMember> byRef = new HashMap<>();
    for (BoardMember member : homedirUsers) {
      byRef.put(member.ref(), member);
    }
    for (BoardMember member : githubUsers) {
      byRef.put(member.ref(), member);
    }
    for (BoardMember member : discordUsers) {
      byRef.put(member.ref(), member);
    }

    Snapshot snapshot =
        new Snapshot(
            List.copyOf(homedirUsers),
            List.copyOf(githubUsers),
            List.copyOf(discordUsers),
            Map.copyOf(byRef),
            Instant.now());
    cache.set(snapshot);
    return snapshot;
  }

  private List<BoardMember> buildHomedirUsers() {
    Map<String, UserProfile> profiles = userProfileService.allProfiles();
    Map<String, BoardMember> deduped = new LinkedHashMap<>();
    for (UserProfile profile : profiles.values()) {
      if (profile == null) {
        continue;
      }
      String canonicalId = normalizeText(firstNonBlank(profile.getUserId(), profile.getEmail()));
      if (canonicalId.isBlank()) {
        continue;
      }
      String displayName = firstNonBlank(profile.getName(), nameFromEmail(profile.getEmail()), "HomeDir member");
      String identifier = maskIdentifier(firstNonBlank(profile.getEmail(), profile.getUserId(), canonicalId));
      String github =
          profile.getGithub() != null && profile.getGithub().login() != null
              ? profile.getGithub().login().trim()
              : null;
      String publicProfilePath = github == null || github.isBlank() ? null : "/u/" + github;
      Instant joinedAt = profile.getGithub() != null ? profile.getGithub().linkedAt() : null;
      String avatarUrl = profile.getGithub() != null ? profile.getGithub().avatarUrl() : null;
      String ref = stableRef("hd", canonicalId);
      deduped.put(
          canonicalId,
          new BoardMember(
              ref,
              "homedir-users",
              displayName,
              identifier,
              avatarUrl,
              joinedAt,
              "/community/member/" + ref,
              publicProfilePath));
    }
    return deduped.values().stream()
        .sorted(Comparator.comparing(member -> member.displayName().toLowerCase(Locale.ROOT)))
        .toList();
  }

  private List<BoardMember> buildGithubUsers() {
    List<CommunityMember> members = communityService.listMembers();
    Map<String, BoardMember> deduped = new LinkedHashMap<>();
    for (CommunityMember member : members) {
      if (member == null || member.getGithub() == null || member.getGithub().isBlank()) {
        continue;
      }
      String github = member.getGithub().trim();
      String key = github.toLowerCase(Locale.ROOT);
      String displayName = firstNonBlank(member.getDisplayName(), github);
      String ref = stableRef("gh", key);
      deduped.put(
          key,
          new BoardMember(
              ref,
              "github-users",
              displayName,
              "@" + github,
              member.getAvatarUrl(),
              member.getJoinedAt(),
              "/community/member/" + ref,
              "/u/" + github));
    }
    return deduped.values().stream()
        .sorted(Comparator.comparing(member -> member.displayName().toLowerCase(Locale.ROOT)))
        .toList();
  }

  private static String normalizeText(String text) {
    if (text == null) {
      return "";
    }
    return text.trim().toLowerCase(Locale.ROOT);
  }

  private static int normalizeLimit(Integer raw) {
    if (raw == null || raw <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(raw, MAX_LIMIT);
  }

  private static boolean matchesQuery(BoardMember member, String normalizedQuery) {
    if (normalizedQuery == null || normalizedQuery.isBlank()) {
      return true;
    }
    return member.displayName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
        || member.identifier().toLowerCase(Locale.ROOT).contains(normalizedQuery);
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return "";
  }

  private static String nameFromEmail(String email) {
    if (email == null || email.isBlank()) {
      return "";
    }
    int at = email.indexOf('@');
    if (at <= 0) {
      return email;
    }
    String local = email.substring(0, at);
    if (local.isBlank()) {
      return "HomeDir member";
    }
    return local;
  }

  private static String maskIdentifier(String raw) {
    if (raw == null || raw.isBlank()) {
      return "hidden";
    }
    String trimmed = raw.trim();
    int at = trimmed.indexOf('@');
    if (at <= 0) {
      return trimmed.length() <= 4 ? trimmed : trimmed.substring(0, 2) + "***" + trimmed.substring(trimmed.length() - 2);
    }
    String local = trimmed.substring(0, at);
    String domain = trimmed.substring(at + 1);
    String localMasked = local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***";
    return localMasked + "@" + domain;
  }

  private static String stableRef(String prefix, String raw) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest((prefix + ":" + raw).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return prefix + "-" + hex.substring(0, 16);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to generate stable member ref", e);
    }
  }

  public record BoardSummary(
      int homedirUsers, int githubUsers, int discordUsers, Instant loadedAt, boolean discordIntegrated) {
    public String loadedAtText() {
      if (loadedAt == null) {
        return "n/a";
      }
      return DateTimeFormatter.ISO_LOCAL_DATE_TIME
          .withZone(ZoneOffset.UTC)
          .format(loadedAt)
          + " UTC";
    }
  }

  public record BoardGroupPage(
      String groupKey,
      String title,
      String description,
      int totalMembers,
      int filteredMembers,
      List<BoardMember> members,
      String query,
      int limit,
      int offset,
      boolean integrationPending,
      Instant loadedAt) {}

  public record BoardMember(
      String ref,
      String groupKey,
      String displayName,
      String identifier,
      String avatarUrl,
      Instant joinedAt,
      String sharePath,
      String publicProfilePath) {
    public String joinedAtText() {
      if (joinedAt == null) {
        return "unknown";
      }
      return DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(joinedAt);
    }
  }

  public record GroupMeta(String key, String title, String description) {
    public static GroupMeta fromKey(String key) {
      String normalized = normalizeText(key);
      return switch (normalized) {
        case "homedir-users" ->
            new GroupMeta(
                "homedir-users",
                "HomeDir users",
                "People who signed in with Google and created a Homedir account.");
        case "github-users" ->
            new GroupMeta(
                "github-users",
                "GitHub users",
                "Contributors with a linked GitHub account in the OSSantiago ecosystem.");
        case "discord-users" ->
            new GroupMeta(
                "discord-users",
                "Discord users",
                "People who joined our official OSSantiago Discord server.");
        default -> null;
      };
    }
  }

  private record Snapshot(
      List<BoardMember> homedirUsers,
      List<BoardMember> githubUsers,
      List<BoardMember> discordUsers,
      Map<String, BoardMember> byRef,
      Instant loadedAt) {
    static Snapshot empty() {
      return new Snapshot(List.of(), List.of(), List.of(), Map.of(), null);
    }
  }
}

