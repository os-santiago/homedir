package com.scanales.eventflow.security;

import jakarta.annotation.Priority;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
public class RateLimitingFilter implements ContainerRequestFilter {

  private static final Logger LOG = Logger.getLogger(RateLimitingFilter.class);

  private static final Set<String> AUTH_PATHS = Set.of("/login", "/auth", "/j_security_check");
  private static final Set<String> LOGOUT_PATHS = Set.of("/logout");
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

  private final Map<String, Counter> counters = new ConcurrentHashMap<>();

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

    Bucket bucket = resolveBucket(path);
    if (bucket == null) {
      return;
    }

    String clientKey = extractClientKey(ctx);
    long now = System.currentTimeMillis();

    Counter c = counters.compute(
        bucket.key(clientKey),
        (k, v) -> {
          if (v == null || now - v.windowStartMs >= bucket.windowMs) {
            return new Counter(now, 1);
          }
          return new Counter(v.windowStartMs, v.count + 1);
        });

    if (now - c.windowStartMs < bucket.windowMs && c.count > bucket.limit) {
      LOG.warnf(
          "Rate limit exceeded bucket=%s client=%s path=%s count=%d limit=%d windowSeconds=%d",
          bucket.name, clientKey, path, c.count, bucket.limit, bucket.windowSeconds);
      ctx.abortWith(
          Response.status(429)
              .entity("Too many requests. Please retry later.")
              .type(MediaType.TEXT_PLAIN)
              .header("Retry-After", bucket.windowSeconds)
              .build());
    }
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

  private Bucket resolveBucket(String path) {
    if (LOGOUT_PATHS.contains(path)) {
      return new Bucket("logout", logoutLimit, windowSeconds);
    }
    if (AUTH_PATHS.contains(path)) {
      return new Bucket("auth", authLimit, windowSeconds);
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
}
