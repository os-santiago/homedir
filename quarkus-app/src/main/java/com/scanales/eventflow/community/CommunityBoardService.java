package com.scanales.eventflow.community;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.scanales.eventflow.model.CommunityMember;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.service.CommunityService;
import com.scanales.eventflow.service.UserProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CommunityBoardService {
  private static final Logger LOG = Logger.getLogger(CommunityBoardService.class);
  private static final int MAX_LIMIT = 100;
  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

  @Inject UserProfileService userProfileService;
  @Inject CommunityService communityService;
  @Inject DiscordGuildStatsService discordGuildStatsService;

  @ConfigProperty(name = "homedir.data.dir", defaultValue = "data")
  String dataDirPath;

  @ConfigProperty(name = "community.board.discord.file")
  Optional<String> discordFileConfig;

  @ConfigProperty(name = "community.board.cache-ttl", defaultValue = "PT1H")
  Duration boardCacheTtl;

  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
  private final ObjectMapper jsonMapper = new ObjectMapper();
  private final AtomicReference<DiscordCache> discordCache = new AtomicReference<>(DiscordCache.empty());

  public CommunityBoardSummary summary() {
    List<CommunityBoardMemberView> discordMembers = discordMembers();
    int discordListedUsers = discordMembers.size();
    DiscordGuildStatsService.DiscordGuildSnapshot discordSnapshot = discordGuildStatsService.snapshot();
    int discordUsers = discordSnapshot.resolveMemberCount(discordListedUsers);
    return new CommunityBoardSummary(
        homedirMembers().size(),
        githubMembers().size(),
        discordUsers,
        discordListedUsers,
        discordSnapshot.onlineCount(),
        resolveDiscordSourceCode(discordSnapshot, discordListedUsers),
        discordSnapshot.loadedAt());
  }

  public BoardSlice list(CommunityBoardGroup group, String query, int limit, int offset) {
    List<CommunityBoardMemberView> source = members(group);
    List<CommunityBoardMemberView> filtered = filter(source, query);
    int normalizedLimit = normalizeLimit(limit);
    int normalizedOffset = Math.max(0, offset);
    if (normalizedOffset >= filtered.size()) {
      return new BoardSlice(filtered.size(), normalizedLimit, normalizedOffset, List.of());
    }
    int end = Math.min(filtered.size(), normalizedOffset + normalizedLimit);
    return new BoardSlice(
        filtered.size(), normalizedLimit, normalizedOffset, filtered.subList(normalizedOffset, end));
  }

  public List<CommunityBoardMemberView> members(CommunityBoardGroup group) {
    return switch (group) {
      case HOMEDIR_USERS -> homedirMembers();
      case GITHUB_USERS -> githubMembers();
      case DISCORD_USERS -> discordMembers();
    };
  }

  void resetDiscordCacheForTests() {
    discordCache.set(DiscordCache.empty());
  }

  public Optional<CommunityBoardMemberView> findMember(CommunityBoardGroup group, String id) {
    String normalizedId = normalizeId(id);
    if (normalizedId == null) {
      return Optional.empty();
    }
    return members(group).stream().filter(member -> normalizedId.equals(member.id())).findFirst();
  }

  private List<CommunityBoardMemberView> homedirMembers() {
    Map<String, CommunityBoardMemberView> byId = new LinkedHashMap<>();
    for (UserProfile profile : userProfileService.allProfiles().values()) {
      String identitySeed = firstNonBlank(profile.getUserId(), profile.getEmail());
      String githubLogin = profile.getGithub() != null ? trimToNull(profile.getGithub().login()) : null;
      String id = homedirMemberId(identitySeed, githubLogin);
      if (id == null) {
        continue;
      }
      String displayName =
          firstNonBlank(profile.getName(), usernameFromEmail(profile.getEmail()), "Homedir user");
      String handle =
          githubLogin != null
              ? "@" + githubLogin
              : firstNonBlank(maskEmail(profile.getEmail()), maskEmail(profile.getUserId()), id);
      String avatarUrl = profile.getGithub() != null ? trimToNull(profile.getGithub().avatarUrl()) : null;
      String since =
          profile.getGithub() != null && profile.getGithub().linkedAt() != null
              ? DATE_FMT.format(profile.getGithub().linkedAt())
              : null;
      String link = memberSharePath(CommunityBoardGroup.HOMEDIR_USERS, id);
      CommunityBoardMemberView candidate =
          new CommunityBoardMemberView(id, displayName, handle, avatarUrl, since, link, link);
      byId.putIfAbsent(id, candidate);
    }
    List<CommunityBoardMemberView> out = new ArrayList<>(byId.values());
    out.sort(memberComparator());
    return List.copyOf(out);
  }

  private List<CommunityBoardMemberView> githubMembers() {
    Map<String, GithubMemberSeed> byLogin = new LinkedHashMap<>();

    for (UserProfile profile : userProfileService.allProfiles().values()) {
      if (profile.getGithub() == null) {
        continue;
      }
      String login = normalizeId(profile.getGithub().login());
      if (login == null) {
        continue;
      }
      String displayName =
          firstNonBlank(profile.getName(), usernameFromEmail(profile.getEmail()), login);
      String avatar = trimToNull(profile.getGithub().avatarUrl());
      Instant linkedAt = profile.getGithub().linkedAt();
      byLogin.merge(
          login, new GithubMemberSeed(displayName, avatar, linkedAt), CommunityBoardService::mergeSeeds);
    }

    for (CommunityMember member : communityService.listMembers()) {
      String login = normalizeId(member.getGithub());
      if (login == null) {
        continue;
      }
      String displayName = firstNonBlank(member.getDisplayName(), login);
      String avatar = trimToNull(member.getAvatarUrl());
      Instant joinedAt = member.getJoinedAt();
      byLogin.merge(
          login, new GithubMemberSeed(displayName, avatar, joinedAt), CommunityBoardService::mergeSeeds);
    }

    List<CommunityBoardMemberView> out = new ArrayList<>();
    for (Map.Entry<String, GithubMemberSeed> entry : byLogin.entrySet()) {
      String login = entry.getKey();
      GithubMemberSeed seed = entry.getValue();
      String link = memberSharePath(CommunityBoardGroup.GITHUB_USERS, login);
      out.add(
          new CommunityBoardMemberView(
              login,
              seed.displayName(),
              "@" + login,
              seed.avatarUrl(),
              seed.memberSince() != null ? DATE_FMT.format(seed.memberSince()) : null,
              link,
              link));
    }
    out.sort(memberComparator());
    return List.copyOf(out);
  }

  private List<CommunityBoardMemberView> discordMembers() {
    Instant now = Instant.now();
    DiscordCache current = discordCache.get();
    if (current.loadedAt() != null && now.isBefore(current.loadedAt().plus(boardCacheTtl))) {
      return current.members();
    }
    Path file = resolveDiscordFile();
    Instant modifiedAt = fileLastModified(file).orElse(null);
    try {
      List<CommunityBoardMemberView> loaded = parseDiscordFile(file);
      DiscordCache next = new DiscordCache(List.copyOf(loaded), now, modifiedAt);
      discordCache.set(next);
      return next.members();
    } catch (Exception e) {
      if (!current.members().isEmpty()) {
        LOG.warnf("Unable to refresh discord users file %s, keeping previous cache", file);
        return current.members();
      }
      LOG.warnf("Unable to load discord users file %s: %s", file, e.getMessage());
      DiscordCache empty = new DiscordCache(List.of(), now, modifiedAt);
      discordCache.set(empty);
      return empty.members();
    }
  }

  private List<CommunityBoardMemberView> parseDiscordFile(Path file) throws Exception {
    if (!Files.exists(file) || !Files.isRegularFile(file)) {
      return List.of();
    }
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    JsonNode root =
        name.endsWith(".json")
            ? jsonMapper.readTree(Files.readString(file))
            : yamlMapper.readTree(Files.readString(file));
    JsonNode membersNode = root.isArray() ? root : root.path("members");
    if (membersNode == null || !membersNode.isArray()) {
      return List.of();
    }
    List<CommunityBoardMemberView> out = new ArrayList<>();
    for (JsonNode node : membersNode) {
      String id = normalizeId(text(node, "id", text(node, "handle", text(node, "display_name", null))));
      if (id == null) {
        continue;
      }
      String displayName = firstNonBlank(text(node, "display_name", null), text(node, "name", null), id);
      String handle = firstNonBlank(text(node, "handle", null), displayName);
      String avatar = text(node, "avatar_url", null);
      String joined = normalizeDateLabel(text(node, "joined_at", null));
      String link = memberSharePath(CommunityBoardGroup.DISCORD_USERS, id);
      out.add(new CommunityBoardMemberView(id, displayName, handle, avatar, joined, link, link));
    }
    out.sort(memberComparator());
    return out;
  }

  private Path resolveDiscordFile() {
    String configured = discordFileConfig.orElse("").trim();
    if (!configured.isEmpty()) {
      return Paths.get(configured);
    }
    String sysProp = System.getProperty("homedir.data.dir");
    String base = (sysProp != null && !sysProp.isBlank()) ? sysProp : dataDirPath;
    return Paths.get(base, "community", "board", "discord-users.yml");
  }

  private Optional<Instant> fileLastModified(Path file) {
    try {
      return Files.exists(file) ? Optional.of(Files.getLastModifiedTime(file).toInstant()) : Optional.empty();
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private List<CommunityBoardMemberView> filter(List<CommunityBoardMemberView> source, String query) {
    if (query == null || query.isBlank()) {
      return source;
    }
    String normalized = query.trim().toLowerCase(Locale.ROOT);
    return source.stream()
        .filter(
            member ->
                contains(member.displayName(), normalized)
                    || contains(member.handle(), normalized)
                    || contains(member.id(), normalized))
        .toList();
  }

  private static boolean contains(String value, String query) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(query);
  }

  private static int normalizeLimit(int value) {
    if (value <= 0) {
      return 30;
    }
    return Math.min(value, MAX_LIMIT);
  }

  private static Comparator<CommunityBoardMemberView> memberComparator() {
    return Comparator.comparing(
            CommunityBoardMemberView::displayName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
        .thenComparing(CommunityBoardMemberView::handle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
  }

  private static String text(JsonNode node, String field, String fallback) {
    if (node == null) {
      return fallback;
    }
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return fallback;
    }
    String out = trimToNull(value.asText(null));
    return out == null ? fallback : out;
  }

  private static String normalizeDateLabel(String raw) {
    String value = trimToNull(raw);
    if (value == null) {
      return null;
    }
    try {
      return DATE_FMT.format(Instant.parse(value));
    } catch (Exception ignored) {
    }
    try {
      return LocalDate.parse(value).toString();
    } catch (Exception ignored) {
    }
    return value;
  }

  private static String usernameFromEmail(String email) {
    String value = trimToNull(email);
    if (value == null) {
      return null;
    }
    int at = value.indexOf('@');
    return at > 0 ? value.substring(0, at) : value;
  }

  private static String maskEmail(String value) {
    String text = trimToNull(value);
    if (text == null) {
      return null;
    }
    int at = text.indexOf('@');
    if (at <= 0) {
      if (text.length() <= 3) {
        return "***";
      }
      return text.substring(0, 2) + "***";
    }
    String local = text.substring(0, at);
    String domain = text.substring(at);
    if (local.length() <= 2) {
      return "*" + domain;
    }
    return local.substring(0, 2) + "***" + domain;
  }

  private static GithubMemberSeed mergeSeeds(GithubMemberSeed left, GithubMemberSeed right) {
    String displayName = firstNonBlank(left.displayName(), right.displayName());
    String avatarUrl = firstNonBlank(left.avatarUrl(), right.avatarUrl());
    Instant memberSince = chooseEarliest(left.memberSince(), right.memberSince());
    return new GithubMemberSeed(displayName, avatarUrl, memberSince);
  }

  private static Instant chooseEarliest(Instant a, Instant b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a.isBefore(b) ? a : b;
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String normalizeId(String raw) {
    String value = trimToNull(raw);
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }

  private static String homedirMemberId(String identitySeed, String githubLogin) {
    String github = normalizeId(githubLogin);
    if (github != null) {
      return "gh-" + github;
    }
    String seed = normalizeId(identitySeed);
    if (seed == null) {
      return null;
    }
    return "hd-" + shortHash(seed, 16);
  }

  private static String memberSharePath(CommunityBoardGroup group, String id) {
    return "/community/member/" + group.path() + "/" + urlEncode(id);
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      String normalized = trimToNull(value);
      if (normalized != null) {
        return normalized;
      }
    }
    return null;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String shortHash(String value, int maxLength) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hashed.length * 2);
      for (byte b : hashed) {
        hex.append(String.format("%02x", b));
      }
      int end = Math.min(hex.length(), Math.max(6, maxLength));
      return hex.substring(0, end);
    } catch (Exception e) {
      return Integer.toHexString(value.hashCode());
    }
  }

  private static String resolveDiscordSourceCode(
      DiscordGuildStatsService.DiscordGuildSnapshot snapshot, int listedUsers) {
    String source = trimToNull(snapshot.sourceCode());
    if (source != null
        && snapshot.loadedAt() != null
        && !"unavailable".equals(source)
        && !"misconfigured".equals(source)
        && !"disabled".equals(source)) {
      return source;
    }
    if (listedUsers > 0) {
      return "file";
    }
    if (source != null) {
      return source;
    }
    return "unavailable";
  }

  public record BoardSlice(
      int total, int limit, int offset, List<CommunityBoardMemberView> items) {}

  private record GithubMemberSeed(String displayName, String avatarUrl, Instant memberSince) {}

  private record DiscordCache(List<CommunityBoardMemberView> members, Instant loadedAt, Instant modifiedAt) {
    static DiscordCache empty() {
      return new DiscordCache(List.of(), null, null);
    }
  }
}
