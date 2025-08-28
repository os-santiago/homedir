package com.scanales.eventflow.notifications;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Configuration properties for notifications. */
@ApplicationScoped
public class NotificationConfig {
  @ConfigProperty(name = "notifications.enabled", defaultValue = "true")
  public boolean enabled;

  @ConfigProperty(name = "notifications.user-cap", defaultValue = "500")
  public int userCap;

  @ConfigProperty(name = "notifications.global-cap", defaultValue = "100000")
  public int globalCap;

  @ConfigProperty(name = "notifications.flush-interval", defaultValue = "PT10S")
  public Duration flushInterval;

  @ConfigProperty(name = "notifications.retention-days", defaultValue = "30")
  public int retentionDays;

  @ConfigProperty(name = "notifications.max-queue-size", defaultValue = "10000")
  public int maxQueueSize;

  @ConfigProperty(name = "notifications.dedupe-window", defaultValue = "PT30M")
  public Duration dedupeWindow;

  @ConfigProperty(name = "notifications.drop-on-queue-full", defaultValue = "false")
  public boolean dropOnQueueFull;

  // Iteration 4 runtime integration

  @ConfigProperty(name = "notifications.scheduler.enabled", defaultValue = "true")
  public boolean schedulerEnabled;

  @ConfigProperty(name = "notifications.scheduler.interval", defaultValue = "PT15S")
  public Duration schedulerInterval;

  @ConfigProperty(name = "notifications.upcoming.window", defaultValue = "PT15M")
  public Duration upcomingWindow;

  @ConfigProperty(name = "notifications.endingSoon.window", defaultValue = "PT10M")
  public Duration endingSoonWindow;

  @ConfigProperty(name = "notifications.sse.enabled", defaultValue = "true")
  public boolean sseEnabled;

  @ConfigProperty(name = "notifications.sse.heartbeat", defaultValue = "PT25S")
  public Duration sseHeartbeat;

  @ConfigProperty(name = "notifications.poll.interval", defaultValue = "PT15S")
  public Duration pollInterval;

  @ConfigProperty(name = "notifications.poll.limit", defaultValue = "20")
  public int pollLimit;

  @ConfigProperty(name = "notifications.stream.maxConnectionsPerUser", defaultValue = "1")
  public int streamMaxConnectionsPerUser;

  // Iteration 6 operability

  @ConfigProperty(name = "notifications.metrics.enabled", defaultValue = "true")
  public boolean metricsEnabled;

  @ConfigProperty(name = "notifications.logs.level", defaultValue = "info")
  public String logsLevel;

  @ConfigProperty(name = "notifications.user-hash.salt", defaultValue = "changeme")
  public String userHashSalt;

  @ConfigProperty(name = "notifications.max-file-size", defaultValue = "3MB")
  public String maxFileSize;

  @ConfigProperty(name = "notifications.maintenance.interval", defaultValue = "PT30M")
  public Duration maintenanceInterval;

  @ConfigProperty(name = "notifications.backpressure.queue.max", defaultValue = "10000")
  public int backpressureQueueMax;

  @ConfigProperty(
      name = "notifications.backpressure.cutoff.evaluator-queue-depth",
      defaultValue = "8000")
  public int evaluatorQueueCutoff;

  @ConfigProperty(name = "notifications.poll.rate-limit.window", defaultValue = "PT30S")
  public Duration pollRateLimitWindow;

  @ConfigProperty(name = "notifications.poll.rate-limit.max", defaultValue = "8")
  public int pollRateLimitMax;
}
