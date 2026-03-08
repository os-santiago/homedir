package com.scanales.eventflow.security;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Simple per-IP rate limiter for auth/public/API endpoints.
 *
 * <p>
 * Uses a fixed window counter per bucket (auth/logout/api) and per client key
 * (first
 * X-Forwarded-For IP or "unknown"). Defaults are conservative and can be tuned
 * via config.
 */
@Provider
@ApplicationScoped
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
public class RateLimitingFilter implements ContainerRequestFilter {

  private static final Logger LOG = Logger.getLogger(RateLimitingFilter.class);

  private static final Set<String> AUTH_PATHS = Set.of("/login", "/auth", "/j_security_check");
  private static final Set<String> LOGOUT_PATHS = Set.of("/logout");
  private static final String COMMUNITY_CONTENT_API_PREFIX = "/api/community/content";
  private static final Set<String> SKIP_PATHS = Set.of("/", "/health", "/metrics");
  private static final Pattern STATIC_PATTERN = Pattern.compile("^/(css|js|images|static|img)/.*");
  private static final Pattern WS_PATTERN = Pattern.compile("^/ws/.*");

  private static final class Counter {
    final long windowStartMs;
    final int count;

    Counter(long windowStartMs, int count) {
      this.windowStartMs = windowStartMs;
      this.count = count;
    }
  }

  @Inject
  @ConfigProperty(name = "rate.limit.enabled", defaultValue = "true")
  boolean enabled;

  @Inject
  @ConfigProperty(name = "rate.limit.window-seconds", defaultValue = "60")
  int windowSeconds;

  @Inject
  @ConfigProperty(name = "rate.limit.auth.limit", defaultValue = "30")
  int authLimit;

  @Inject
  @ConfigProperty(name = "rate.limit.logout.limit", defaultValue = "30")
  int logoutLimit;

  @Inject
  @ConfigProperty(name = "rate.limit.api.limit", defaultValue = "120")
  int apiLimit;

  @Inject
  @ConfigProperty(name = "rate.limit.api.community-content.limit", defaultValue = "600")
  int communityContentApiLimit;

  @Inject
  @ConfigProperty(name = "rate.limit.api.community-content.read.limit", defaultValue = "1800")
  int communityContentApiReadLimit;

  @Inject
  @ConfigProperty(name = "rate.limit.api.community-content.write.limit", defaultValue = "600")
  int communityContentApiWriteLimit;

  @Inject
  @ConfigProperty(
      name = "rate.limit.api.community-content.adaptive.enabled",
      defaultValue = "true")
  boolean communityContentAdaptiveEnabled;

  @Inject
  @ConfigProperty(
      name = "rate.limit.api.community-content.adaptive.per-fingerprint-bonus",
      defaultValue = "120")
  int communityContentAdaptivePerFingerprintBonus;

  @Inject
  @ConfigProperty(
      name = "rate.limit.api.community-content.adaptive.max-fingerprints",
      defaultValue = "20")
  int communityContentAdaptiveMaxFingerprints;

  @Inject
  @ConfigProperty(
      name = "rate.limit.api.community-content.adaptive.max-limit",
      defaultValue = "2400")
  int communityContentAdaptiveMaxLimit;

  private final Map<String, Counter> counters = new ConcurrentHashMap<>();
  private final Map<String, FingerprintWindow> communityContentFingerprintWindows =
      new ConcurrentHashMap<>();
  private final AtomicLong totalChecked = new AtomicLong();
  private final AtomicLong totalThrottled = new AtomicLong();
  private final AtomicLong adaptiveLimitApplied = new AtomicLong();
  private final Map<String, AtomicLong> checkedByBucket = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> throttledByBucket = new ConcurrentHashMap<>();
  private final AtomicLong cleanupTicker = new AtomicLong();

  @Override
  public void filter(ContainerRequestContext ctx) {
    if (!enabled) {
      return;
    }

    String rawPath = ctx.getUriInfo().getPath();
    String path = rawPath.startsWith("/") ? rawPath : "/" + rawPath;

    if (shouldSkip(path, ctx)) {
      return;
    }

    Bucket bucket = resolveBucket(path, ctx.getMethod());
    if (bucket == null) {
      return;
    }
    totalChecked.incrementAndGet();
    checkedByBucket.computeIfAbsent(bucket.name, key -> new AtomicLong()).incrementAndGet();

    String clientKey = extractClientKey(ctx);
    long now = System.currentTimeMillis();

    int effectiveLimit = effectiveLimit(bucket, clientKey, ctx, now);
    Counter c = counters.compute(
        bucket.key(clientKey),
        (k, v) -> {
          if (v == null || now - v.windowStartMs >= bucket.windowMs) {
            return new Counter(now, 1);
          }
          return new Counter(v.windowStartMs, v.count + 1);
        });

    if (now - c.windowStartMs < bucket.windowMs && c.count > effectiveLimit) {
      totalThrottled.incrementAndGet();
      throttledByBucket.computeIfAbsent(bucket.name, key -> new AtomicLong()).incrementAndGet();
      LOG.warnf(
          "Rate limit exceeded bucket=%s client=%s path=%s count=%d limit=%d windowSeconds=%d",
          bucket.name, clientKey, path, c.count, effectiveLimit, bucket.windowSeconds);
      ctx.abortWith(
          Response.status(429)
              .entity("Too many requests. Please retry later.")
              .type(MediaType.TEXT_PLAIN)
              .header("Retry-After", bucket.windowSeconds)
              .build());
    }

    maybeCleanupAdaptiveWindows(now, bucket.windowMs);
  }

  public RateLimitStats stats() {
    return new RateLimitStats(
        enabled,
        windowSeconds,
        authLimit,
        logoutLimit,
        apiLimit,
        communityContentApiLimit,
        communityContentApiReadLimit,
        communityContentApiWriteLimit,
        communityContentAdaptiveEnabled,
        communityContentAdaptivePerFingerprintBonus,
        communityContentAdaptiveMaxFingerprints,
        communityContentAdaptiveMaxLimit,
        communityContentFingerprintWindows.size(),
        adaptiveLimitApplied.get(),
        totalChecked.get(),
        totalThrottled.get(),
        snapshot(checkedByBucket),
        snapshot(throttledByBucket));
  }

  private boolean shouldSkip(String path, ContainerRequestContext ctx) {
    if (SKIP_PATHS.contains(path)) {
      return true;
    }
    if (STATIC_PATTERN.matcher(path).matches()) {
      return true;
    }
    if (WS_PATTERN.matcher(path).matches()) {
      return true;
    }
    String accept = ctx.getHeaderString(HttpHeaders.ACCEPT);
    if (accept != null && accept.contains("text/event-stream")) {
      // avoid throttling SSE
      return true;
    }
    return false;
  }

  private Bucket resolveBucket(String path, String method) {
    String normalizedMethod = method == null || method.isBlank() ? "GET" : method.trim().toUpperCase();
    if (LOGOUT_PATHS.contains(path)) {
      return new Bucket("logout", logoutLimit, windowSeconds);
    }
    if (AUTH_PATHS.contains(path)) {
      return new Bucket("auth", authLimit, windowSeconds);
    }
    if (path.startsWith(COMMUNITY_CONTENT_API_PREFIX)) {
      int communityLimit =
          "GET".equals(normalizedMethod)
              ? Math.max(communityContentApiLimit, communityContentApiReadLimit)
              : Math.max(1, communityContentApiWriteLimit);
      return new Bucket("api-community-content", communityLimit, windowSeconds);
    }
    if (path.startsWith("/api") || path.startsWith("/private") || path.startsWith("/public")) {
      return new Bucket("api", apiLimit, windowSeconds);
    }
    return null;
  }

  private String extractClientKey(ContainerRequestContext ctx) {
    String xff = ctx.getHeaderString("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    String cfConnectingIp = ctx.getHeaderString("CF-Connecting-IP");
    if (cfConnectingIp != null && !cfConnectingIp.isBlank()) {
      return cfConnectingIp.trim();
    }
    String realIp = ctx.getHeaderString("X-Real-IP");
    if (realIp != null && !realIp.isBlank()) {
      return realIp.trim();
    }
    return "unknown";
  }

  private static final class Bucket {
    final String name;
    final int limit;
    final long windowMs;
    final int windowSeconds;

    Bucket(String name, int limit, int windowSeconds) {
      this.name = name;
      this.limit = limit;
      this.windowSeconds = windowSeconds;
      this.windowMs = Duration.ofSeconds(windowSeconds).toMillis();
    }

    String key(String client) {
      return name + "|" + client;
    }
  }

  private static final class FingerprintWindow {
    final long windowStartMs;
    final Set<String> fingerprints;

    FingerprintWindow(long windowStartMs, Set<String> fingerprints) {
      this.windowStartMs = windowStartMs;
      this.fingerprints = fingerprints;
    }
  }

  private int effectiveLimit(
      Bucket bucket, String clientKey, ContainerRequestContext ctx, long now) {
    if (!"api-community-content".equals(bucket.name) || !communityContentAdaptiveEnabled) {
      return bucket.limit;
    }
    if (communityContentAdaptivePerFingerprintBonus <= 0 || communityContentAdaptiveMaxLimit <= bucket.limit) {
      return bucket.limit;
    }

    String fingerprint = extractCommunityContentFingerprint(ctx);
    FingerprintWindow state =
        communityContentFingerprintWindows.compute(
            clientKey,
            (key, current) -> {
              if (current == null || now - current.windowStartMs >= bucket.windowMs) {
                Set<String> values = new HashSet<>();
                values.add(fingerprint);
                return new FingerprintWindow(now, values);
              }
              Set<String> values = new HashSet<>(current.fingerprints);
              if (values.size() < Math.max(1, communityContentAdaptiveMaxFingerprints)) {
                values.add(fingerprint);
              }
              return new FingerprintWindow(current.windowStartMs, values);
            });

    int distinctFingerprints = state.fingerprints.size();
    int bonusUnits =
        Math.max(0, Math.min(distinctFingerprints - 1, Math.max(0, communityContentAdaptiveMaxFingerprints - 1)));
    int adaptiveLimit = bucket.limit + (bonusUnits * communityContentAdaptivePerFingerprintBonus);
    int effectiveLimit = Math.min(Math.max(bucket.limit, adaptiveLimit), communityContentAdaptiveMaxLimit);
    if (effectiveLimit > bucket.limit) {
      adaptiveLimitApplied.incrementAndGet();
    }
    return effectiveLimit;
  }

  private String extractCommunityContentFingerprint(ContainerRequestContext ctx) {
    String sessionCookie =
        extractCookieValue(ctx.getHeaderString(HttpHeaders.COOKIE), "q_session", "JSESSIONID", "session", "connect.sid");
    String userAgent = nullToEmpty(ctx.getHeaderString(HttpHeaders.USER_AGENT));
    String acceptLanguage = nullToEmpty(ctx.getHeaderString(HttpHeaders.ACCEPT_LANGUAGE));
    String accept = nullToEmpty(ctx.getHeaderString(HttpHeaders.ACCEPT));
    String method = nullToEmpty(ctx.getMethod());
    String fingerprintMaterial = sessionCookie + "|" + userAgent + "|" + acceptLanguage + "|" + accept + "|" + method;
    return Integer.toHexString(Objects.hash(fingerprintMaterial));
  }

  private static String extractCookieValue(String cookieHeader, String... names) {
    if (cookieHeader == null || cookieHeader.isBlank() || names == null || names.length == 0) {
      return "";
    }
    String[] parts = cookieHeader.split(";");
    for (String part : parts) {
      if (part == null || part.isBlank()) {
        continue;
      }
      String[] keyValue = part.trim().split("=", 2);
      if (keyValue.length != 2) {
        continue;
      }
      String key = keyValue[0].trim();
      for (String name : names) {
        if (name.equalsIgnoreCase(key)) {
          return keyValue[1].trim();
        }
      }
    }
    return "";
  }

  private void maybeCleanupAdaptiveWindows(long now, long windowMs) {
    long tick = cleanupTicker.incrementAndGet();
    if (tick % 5_000 != 0) {
      return;
    }
    long staleAfterMs = Math.max(windowMs * 2L, Duration.ofMinutes(5).toMillis());
    communityContentFingerprintWindows.entrySet().removeIf(entry -> now - entry.getValue().windowStartMs >= staleAfterMs);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static Map<String, Long> snapshot(Map<String, AtomicLong> source) {
    if (source.isEmpty()) {
      return Map.of();
    }
    Map<String, Long> out = new ConcurrentHashMap<>();
    source.forEach((key, value) -> out.put(key, value.get()));
    return Map.copyOf(out);
  }

  public record RateLimitStats(
      boolean enabled,
      int windowSeconds,
      int authLimit,
      int logoutLimit,
      int apiLimit,
      int communityContentApiLimit,
      int communityContentApiReadLimit,
      int communityContentApiWriteLimit,
      boolean communityContentAdaptiveEnabled,
      int communityContentAdaptivePerFingerprintBonus,
      int communityContentAdaptiveMaxFingerprints,
      int communityContentAdaptiveMaxLimit,
      int communityContentAdaptiveTrackedIps,
      long adaptiveLimitApplied,
      long totalChecked,
      long totalThrottled,
      Map<String, Long> checkedByBucket,
      Map<String, Long> throttledByBucket) {
  }
}
