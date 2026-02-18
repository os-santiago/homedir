package com.scanales.eventflow.community;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DiscordGuildStatsService {
  private static final Logger LOG = Logger.getLogger(DiscordGuildStatsService.class);
  private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(1);
  private static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofMinutes(5);
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

  @Inject ObjectMapper objectMapper;

  @ConfigProperty(name = "community.board.discord.integration.enabled", defaultValue = "false")
  boolean integrationEnabled;

  @ConfigProperty(name = "community.board.discord.guild-id")
  Optional<String> guildIdConfig;

  @ConfigProperty(name = "community.board.discord.bot-token")
  Optional<String> botTokenConfig;

  @ConfigProperty(name = "community.board.discord.cache-ttl", defaultValue = "PT1H")
  Duration cacheTtl;

  @ConfigProperty(name = "community.board.discord.retry-interval", defaultValue = "PT5M")
  Duration retryInterval;

  @ConfigProperty(name = "community.board.discord.request-timeout", defaultValue = "PT5S")
  Duration requestTimeout;

  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(DEFAULT_TIMEOUT).build();

  private final AtomicReference<DiscordGuildSnapshot> snapshot =
      new AtomicReference<>(DiscordGuildSnapshot.disabled(null));
  private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
  private final ExecutorService refreshExecutor =
      Executors.newSingleThreadExecutor(
          new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
              Thread thread = new Thread(runnable, "discord-board-refresh");
              thread.setDaemon(true);
              return thread;
            }
          });

  @PostConstruct
  void init() {
    triggerRefreshAsync(true, "startup");
  }

  @PreDestroy
  void shutdown() {
    refreshExecutor.shutdownNow();
  }

  @Scheduled(every = "{community.board.discord.refresh-interval:30m}")
  void scheduledRefresh() {
    triggerRefreshAsync(true, "schedule");
  }

  public DiscordGuildSnapshot snapshot() {
    triggerRefreshAsync(false, "on-demand");
    return snapshot.get();
  }

  void resetForTests() {
    snapshot.set(DiscordGuildSnapshot.disabled(null));
  }

  private void triggerRefreshAsync(boolean force, String reason) {
    DiscordGuildSnapshot current = snapshot.get();
    Instant now = Instant.now();
    if (!force && !shouldRefresh(current, now)) {
      return;
    }
    if (!refreshInProgress.compareAndSet(false, true)) {
      return;
    }
    refreshExecutor.submit(
        () -> {
          try {
            refresh(reason, now);
          } finally {
            refreshInProgress.set(false);
          }
        });
  }

  private boolean shouldRefresh(DiscordGuildSnapshot current, Instant now) {
    if (!integrationEnabled) {
      return false;
    }
    if (current.loadedAt() != null && now.isBefore(current.loadedAt().plus(effectiveCacheTtl()))) {
      return false;
    }
    if (current.lastAttemptAt() == null) {
      return true;
    }
    return now.isAfter(current.lastAttemptAt().plus(effectiveRetryInterval()));
  }

  private void refresh(String reason, Instant attemptedAt) {
    DiscordGuildSnapshot previous = snapshot.get();

    if (!integrationEnabled) {
      snapshot.set(DiscordGuildSnapshot.disabled(attemptedAt));
      return;
    }

    String guildId = trimToNull(guildIdConfig.orElse(null));
    if (guildId == null) {
      snapshot.set(DiscordGuildSnapshot.misconfigured(attemptedAt));
      LOG.warn("Discord board integration enabled but community.board.discord.guild-id is missing");
      return;
    }

    Optional<DiscordFetchResult> loaded = fetchFromDiscord(guildId);
    if (loaded.isPresent()) {
      DiscordFetchResult result = loaded.get();
      snapshot.set(
          new DiscordGuildSnapshot(
              true,
              true,
              result.memberCount(),
              result.onlineCount(),
              result.sourceCode(),
              result.memberSamples(),
              attemptedAt,
              attemptedAt,
              true));
      LOG.infov(
          "Discord board stats refreshed reason={0} source={1} members={2} online={3}",
          reason,
          result.sourceCode(),
          result.memberCount(),
          result.onlineCount());
      return;
    }

    if (previous.loadedAt() != null
        && (previous.memberCount() != null || previous.onlineCount() != null)
        && previous.configured()) {
      snapshot.set(previous.withFailedAttempt(attemptedAt));
      LOG.warnv(
          "Discord board refresh failed reason={0}; keeping cached source={1}",
          reason,
          previous.sourceCode());
      return;
    }

    snapshot.set(DiscordGuildSnapshot.unavailable(attemptedAt));
    LOG.warnv("Discord board refresh failed reason={0}; no data available", reason);
  }

  private Optional<DiscordFetchResult> fetchFromDiscord(String guildId) {
    String token = trimToNull(botTokenConfig.orElse(null));
    if (token != null) {
      Optional<DiscordFetchResult> botResult =
          fetchJson(
              "https://discord.com/api/v10/guilds/"
                  + url(guildId)
                  + "?with_counts=true",
              "Bot " + token,
              "bot_api");
      if (botResult.isPresent()) {
        return Optional.of(enrichWithWidgetMembers(guildId, botResult.get()));
      }
    }

    Optional<DiscordFetchResult> previewResult =
        fetchJson(
            "https://discord.com/api/v10/guilds/" + url(guildId) + "/preview",
            null,
            "preview_api");
    if (previewResult.isPresent()) {
      return Optional.of(enrichWithWidgetMembers(guildId, previewResult.get()));
    }

    return fetchJson(
        "https://discord.com/api/guilds/" + url(guildId) + "/widget.json",
        null,
        "widget_api");
  }

  private DiscordFetchResult enrichWithWidgetMembers(String guildId, DiscordFetchResult current) {
    if (!current.memberSamples().isEmpty() || "widget_api".equals(current.sourceCode())) {
      return current;
    }
    Optional<DiscordFetchResult> widgetResult =
        fetchJson(
            "https://discord.com/api/guilds/" + url(guildId) + "/widget.json",
            null,
            "widget_api_members");
    if (widgetResult.isEmpty() || widgetResult.get().memberSamples().isEmpty()) {
      return current;
    }
    return current.withMemberSamples(widgetResult.get().memberSamples());
  }

  private Optional<DiscordFetchResult> fetchJson(String url, String authorization, String sourceCode) {
    try {
      HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(effectiveRequestTimeout())
          .header("Accept", "application/json")
          .header("User-Agent", "homedir-community-board");
      if (authorization != null && !authorization.isBlank()) {
        builder.header("Authorization", authorization);
      }
      HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        LOG.debugv("Discord {0} request failed with status={1}", sourceCode, response.statusCode());
        return Optional.empty();
      }
      JsonNode root = objectMapper.readTree(response.body());
      Integer memberCount = firstInt(root, "approximate_member_count", "member_count");
      Integer onlineCount = firstInt(root, "approximate_presence_count", "presence_count");
      List<DiscordMemberSample> memberSamples = parseMemberSamples(root);
      if (memberCount == null && onlineCount == null && memberSamples.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(new DiscordFetchResult(memberCount, onlineCount, sourceCode, memberSamples));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warnv("Discord {0} request interrupted", sourceCode);
      return Optional.empty();
    } catch (Exception e) {
      LOG.warnv("Discord {0} request failed: {1}", sourceCode, e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  static Integer firstInt(JsonNode root, String... keys) {
    if (root == null || keys == null) {
      return null;
    }
    for (String key : keys) {
      if (key == null || key.isBlank()) {
        continue;
      }
      JsonNode node = root.path(key);
      if (node.isMissingNode() || node.isNull()) {
        continue;
      }
      if (node.isInt() || node.isLong()) {
        return node.asInt();
      }
      String text = trimToNull(node.asText(null));
      if (text == null) {
        continue;
      }
      try {
        return Integer.parseInt(text);
      } catch (Exception ignored) {
      }
    }
    return null;
  }

  static List<DiscordMemberSample> parseMemberSamples(JsonNode root) {
    if (root == null) {
      return List.of();
    }
    JsonNode membersNode = root.path("members");
    if (!membersNode.isArray()) {
      return List.of();
    }
    List<DiscordMemberSample> out = new ArrayList<>();
    for (JsonNode memberNode : membersNode) {
      String id = trimToNull(memberNode.path("id").asText(null));
      String username = trimToNull(memberNode.path("username").asText(null));
      String globalName = trimToNull(memberNode.path("global_name").asText(null));
      String displayName = firstNonBlank(globalName, username, id);
      String discriminator = trimToNull(memberNode.path("discriminator").asText(null));
      String handle = username;
      if (handle != null && discriminator != null && !"0".equals(discriminator)) {
        handle = handle + "#" + discriminator;
      }
      String avatarUrl = trimToNull(memberNode.path("avatar_url").asText(null));
      String normalizedId = normalizeMemberId(id, username);
      if (normalizedId == null || displayName == null) {
        continue;
      }
      out.add(new DiscordMemberSample(normalizedId, displayName, firstNonBlank(handle, displayName), avatarUrl));
    }
    return List.copyOf(out);
  }

  private Duration effectiveCacheTtl() {
    if (cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()) {
      return DEFAULT_CACHE_TTL;
    }
    return cacheTtl;
  }

  private Duration effectiveRetryInterval() {
    if (retryInterval == null || retryInterval.isNegative() || retryInterval.isZero()) {
      return DEFAULT_RETRY_INTERVAL;
    }
    return retryInterval;
  }

  private Duration effectiveRequestTimeout() {
    if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
      return DEFAULT_TIMEOUT;
    }
    return requestTimeout;
  }

  private static String url(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String normalizeMemberId(String id, String username) {
    String byId = trimToNull(id);
    if (byId != null) {
      return byId;
    }
    String byUsername = trimToNull(username);
    if (byUsername == null) {
      return null;
    }
    return byUsername.toLowerCase(Locale.ROOT);
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

  private record DiscordFetchResult(
      Integer memberCount, Integer onlineCount, String sourceCode, List<DiscordMemberSample> memberSamples) {
    DiscordFetchResult withMemberSamples(List<DiscordMemberSample> samples) {
      if (samples == null || samples.isEmpty()) {
        return this;
      }
      return new DiscordFetchResult(memberCount, onlineCount, sourceCode, List.copyOf(samples));
    }
  }

  public record DiscordMemberSample(String id, String displayName, String handle, String avatarUrl) {}

  public record DiscordGuildSnapshot(
      boolean integrationEnabled,
      boolean configured,
      Integer memberCount,
      Integer onlineCount,
      String sourceCode,
      List<DiscordMemberSample> memberSamples,
      Instant loadedAt,
      Instant lastAttemptAt,
      boolean lastAttemptSucceeded) {

    static DiscordGuildSnapshot disabled(Instant attempt) {
      return new DiscordGuildSnapshot(false, false, null, null, "disabled", List.of(), null, attempt, false);
    }

    static DiscordGuildSnapshot misconfigured(Instant attempt) {
      return new DiscordGuildSnapshot(true, false, null, null, "misconfigured", List.of(), null, attempt, false);
    }

    static DiscordGuildSnapshot unavailable(Instant attempt) {
      return new DiscordGuildSnapshot(true, true, null, null, "unavailable", List.of(), null, attempt, false);
    }

    DiscordGuildSnapshot withFailedAttempt(Instant attempt) {
      return new DiscordGuildSnapshot(
          integrationEnabled,
          configured,
          memberCount,
          onlineCount,
          sourceCode,
          memberSamples,
          loadedAt,
          attempt,
          false);
    }

    public int resolveMemberCount(int fallback) {
      if (memberCount != null && memberCount > 0) {
        return memberCount;
      }
      return Math.max(0, fallback);
    }
  }
}
