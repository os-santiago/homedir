package com.scanales.eventflow.community;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.scanales.eventflow.model.CommunityMember;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.service.CommunityService;
import com.scanales.eventflow.service.UserProfileService;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
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

  @ConfigProperty(name = "community.board.snapshot-max-age", defaultValue = "PT45S")
  Duration snapshotMaxAge;

  @ConfigProperty(name = "community.board.response-cache-ttl", defaultValue = "PT10S")
  Duration responseCacheTtl;

  @ConfigProperty(name = "community.board.empty-snapshot-retry-interval", defaultValue = "PT12S")
  Duration emptySnapshotRetryInterval;

  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
  private final ObjectMapper jsonMapper = new ObjectMapper();
  private final AtomicReference<DiscordCache> discordCache = new AtomicReference<>(DiscordCache.empty());
  private final AtomicReference<BoardSnapshot> snapshot = new AtomicReference<>(BoardSnapshot.empty());
  private final Map<ResponseKey, ResponseCacheEntry> responseCache = new ConcurrentHashMap<>();
  private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
  private final ExecutorService refreshExecutor =
      Executors.newSingleThreadExecutor(
          new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
              Thread thread = new Thread(runnable, "community-board-refresh");
              thread.setDaemon(true);
              return thread;
            }
          });

  @PostConstruct
  void init() {
    refreshNow("startup");
  }

  @PreDestroy
  void shutdown() {
    refreshExecutor.shutdownNow();
  }

  @Scheduled(every = "{community.board.snapshot-refresh-every:30s}")
  void scheduledRefresh() {
    triggerRefreshAsync(true, "schedule");
  }

  public CommunityBoardSummary summary() {
    maybeRefreshAsyncOnDemand();
    BoardSnapshot current = snapshot.get();
    List<CommunityBoardMemberView> discordMembers =
        current.membersByGroup().getOrDefault(CommunityBoardGroup.DISCORD_USERS, List.of());
    int discordListedUsers = discordMembers.size();
    DiscordGuildStatsService.DiscordGuildSnapshot discordSnapshot = discordGuildStatsService.snapshot();
    int discordUsers = discordSnapshot.resolveMemberCount(discordListedUsers);
    return new CommunityBoardSummary(
        current.membersByGroup().getOrDefault(CommunityBoardGroup.HOMEDIR_USERS, List.of()).size(),
        current.membersByGroup().getOrDefault(CommunityBoardGroup.GITHUB_USERS, List.of()).size(),
        discordUsers,
        discordListedUsers,
        discordSnapshot.onlineCount(),
        resolveDiscordSourceCode(discordSnapshot, discordListedUsers),
        discordSnapshot.loadedAt());
  }

  public BoardSlice list(CommunityBoardGroup group, String query, int limit, int offset) {
    maybeRefreshAsyncOnDemand();
    String normalizedQuery = normalizeQuery(query);
    List<CommunityBoardMemberView> source = members(group);
    int normalizedLimit = normalizeLimit(limit);
    int normalizedOffset = Math.max(0, offset);
    ResponseKey cacheKey = new ResponseKey(group, normalizedQuery, normalizedLimit, normalizedOffset);
    ResponseCacheEntry cached = responseCache.get(cacheKey);
    Instant now = Instant.now();
    if (cached != null && now.isBefore(cached.cachedAt().plus(effectiveResponseCacheTtl()))) {
      return cached.slice();
    }
    List<CommunityBoardMemberView> filtered = filter(source, normalizedQuery);
    BoardSlice slice;
    if (normalizedOffset >= filtered.size()) {
      slice = new BoardSlice(filtered.size(), normalizedLimit, normalizedOffset, List.of());
      responseCache.put(cacheKey, new ResponseCacheEntry(slice, now));
      return slice;
    }
    int end = Math.min(filtered.size(), normalizedOffset + normalizedLimit);
    slice =
        new BoardSlice(
        filtered.size(), normalizedLimit, normalizedOffset, filtered.subList(normalizedOffset, end));
    responseCache.put(cacheKey, new ResponseCacheEntry(slice, now));
    return slice;
  }

  public List<CommunityBoardMemberView> members(CommunityBoardGroup group) {
    return snapshot.get().membersByGroup().getOrDefault(group, List.of());
  }

  void resetDiscordCacheForTests() {
    discordCache.set(DiscordCache.empty());
    snapshot.set(BoardSnapshot.empty());
    responseCache.clear();
    refreshNow("test-reset");
  }

  public Optional<CommunityBoardMemberView> findMember(CommunityBoardGroup group, String id) {
    String normalizedId = normalizeId(id);
    if (normalizedId == null) {
      return Optional.empty();
    }
    return members(group).stream().filter(member -> normalizedId.equals(member.id())).findFirst();
  }

  private void maybeRefreshAsyncOnDemand() {
    BoardSnapshot current = snapshot.get();
    if (current.refreshedAt() == null) {
      triggerRefreshAsync(false, "empty-snapshot");
      return;
    }
    if (Instant.now().isAfter(current.refreshedAt().plus(effectiveSnapshotMaxAge()))) {
      triggerRefreshAsync(false, "stale-snapshot");
    }
  }

  private void triggerRefreshAsync(boolean force, String reason) {
    BoardSnapshot current = snapshot.get();
    boolean emptyRecoverWindow =
        current.refreshedAt() != null
            && current.totalMembers() == 0
            && Instant.now().isAfter(current.refreshedAt().plus(effectiveEmptySnapshotRetryInterval()));
    boolean stale =
        current.refreshedAt() == null
            || Instant.now().isAfter(current.refreshedAt().plus(effectiveSnapshotMaxAge()))
            || emptyRecoverWindow;
    if (!force && !stale) {
      return;
    }
    if (!refreshInProgress.compareAndSet(false, true)) {
      return;
    }
    refreshExecutor.submit(
        () -> {
          try {
            refreshNow(reason);
          } finally {
            refreshInProgress.set(false);
          }
        });
  }

  private void refreshNow(String reason) {
    Instant started = Instant.now();
    try {
      List<CommunityBoardMemberView> homedirMembers = loadHomedirMembers();
      List<CommunityBoardMemberView> githubMembers = loadGithubMembers();
      List<CommunityBoardMemberView> discordMembers = loadDiscordMembers();

      Map<CommunityBoardGroup, List<CommunityBoardMemberView>> byGroup =
          Map.of(
              CommunityBoardGroup.HOMEDIR_USERS, List.copyOf(homedirMembers),
              CommunityBoardGroup.GITHUB_USERS, List.copyOf(githubMembers),
              CommunityBoardGroup.DISCORD_USERS, List.copyOf(discordMembers));
      long durationMs = Duration.between(started, Instant.now()).toMillis();
      BoardSnapshot refreshed = new BoardSnapshot(Map.copyOf(byGroup), Instant.now(), durationMs);
      snapshot.set(refreshed);
      responseCache.clear();
      LOG.infov(
          "Community board snapshot refreshed reason={0} homedir={1} github={2} discord={3} durationMs={4}",
          reason,
          homedirMembers.size(),
          githubMembers.size(),
          discordMembers.size(),
          durationMs);
    } catch (Exception e) {
      LOG.warnf("Community board snapshot refresh failed reason=%s message=%s", reason, e.getMessage());
    }
  }

  private List<CommunityBoardMemberView> loadHomedirMembers() {
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

  private List<CommunityBoardMemberView> loadGithubMembers() {
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

  private List<CommunityBoardMemberView> loadDiscordMembers() {
    Instant now = Instant.now();
    DiscordCache current = discordCache.get();
    if (current.loadedAt() != null && now.isBefore(current.loadedAt().plus(boardCacheTtl))) {
      return current.members();
    }
    Path file = resolveDiscordFile();
    Instant modifiedAt = fileLastModified(file).orElse(null);
    try {
      List<CommunityBoardMemberView> loaded = parseDiscordFile(file);
      if (loaded.isEmpty()) {
        loaded = fallbackDiscordMembers();
      }
      DiscordCache next = new DiscordCache(List.copyOf(loaded), now, modifiedAt);
      discordCache.set(next);
      return next.members();
    } catch (Exception e) {
      List<CommunityBoardMemberView> fallback = fallbackDiscordMembers();
      if (!fallback.isEmpty()) {
        DiscordCache next = new DiscordCache(List.copyOf(fallback), now, modifiedAt);
        discordCache.set(next);
        return next.members();
      }
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

  private List<CommunityBoardMemberView> fallbackDiscordMembers() {
    DiscordGuildStatsService.DiscordGuildSnapshot snapshot = discordGuildStatsService.snapshot();
    List<DiscordGuildStatsService.DiscordMemberSample> samples = snapshot.memberSamples();
    if (samples == null || samples.isEmpty()) {
      return List.of();
    }
    Map<String, CommunityBoardMemberView> byId = new LinkedHashMap<>();
    for (DiscordGuildStatsService.DiscordMemberSample sample : samples) {
      String id = normalizeId(sample.id());
      if (id == null) {
        continue;
      }
      String displayName = firstNonBlank(sample.displayName(), sample.handle(), id);
      String handle = firstNonBlank(sample.handle(), displayName);
      String link = memberSharePath(CommunityBoardGroup.DISCORD_USERS, id);
      CommunityBoardMemberView member =
          new CommunityBoardMemberView(id, displayName, handle, sample.avatarUrl(), null, link, link);
      byId.putIfAbsent(id, member);
    }
    List<CommunityBoardMemberView> out = new ArrayList<>(byId.values());
    out.sort(memberComparator());
    return List.copyOf(out);
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
    return source.stream()
        .filter(
            member ->
                contains(member.displayName(), query)
                    || contains(member.handle(), query)
                    || contains(member.id(), query))
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

  private static String normalizeQuery(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  private Duration effectiveSnapshotMaxAge() {
    if (snapshotMaxAge == null || snapshotMaxAge.isNegative() || snapshotMaxAge.isZero()) {
      return Duration.ofSeconds(45);
    }
    return snapshotMaxAge;
  }

  private Duration effectiveResponseCacheTtl() {
    if (responseCacheTtl == null || responseCacheTtl.isNegative() || responseCacheTtl.isZero()) {
      return Duration.ofSeconds(10);
    }
    return responseCacheTtl;
  }

  private Duration effectiveEmptySnapshotRetryInterval() {
    if (emptySnapshotRetryInterval == null
        || emptySnapshotRetryInterval.isNegative()
        || emptySnapshotRetryInterval.isZero()) {
      return Duration.ofSeconds(12);
    }
    return emptySnapshotRetryInterval;
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

  private record BoardSnapshot(
      Map<CommunityBoardGroup, List<CommunityBoardMemberView>> membersByGroup,
      Instant refreshedAt,
      long durationMs) {
    static BoardSnapshot empty() {
      return new BoardSnapshot(Map.of(), null, 0L);
    }

    int totalMembers() {
      if (membersByGroup == null || membersByGroup.isEmpty()) {
        return 0;
      }
      int total = 0;
      for (List<CommunityBoardMemberView> members : membersByGroup.values()) {
        total += members == null ? 0 : members.size();
      }
      return total;
    }
  }

  private record ResponseKey(CommunityBoardGroup group, String query, int limit, int offset) {}

  private record ResponseCacheEntry(BoardSlice slice, Instant cachedAt) {}

  private record DiscordCache(List<CommunityBoardMemberView> members, Instant loadedAt, Instant modifiedAt) {
    static DiscordCache empty() {
      return new DiscordCache(List.of(), null, null);
    }
  }
}
