package com.scanales.eventflow.notifications;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

/**
 * Configuration properties for notifications.
 *
 * <p>This bean deliberately exposes public static fields so that tests can easily tweak
 * configuration at runtime and have the changes reflected everywhere the configuration is used.
 * During startup the fields are populated from MicroProfile configuration with sensible defaults so
 * production usage is unaffected.
 */
@Singleton
public class NotificationConfig {
  private static final Logger LOG = Logger.getLogger(NotificationConfig.class);
  private static final int MIN_SALT_LENGTH = 16;
  private static final int RANDOM_SALT_BYTES = 32;

  public static boolean enabled = true;
  public static int userCap = 500;
  public static int globalCap = 100000;
  public static Duration flushInterval = Duration.parse("PT10S");
  public static int retentionDays = 30;
  public static int maxQueueSize = 10000;
  public static Duration dedupeWindow = Duration.parse("PT30M");
  public static boolean dropOnQueueFull = false;

  // Iteration 4 runtime integration
  public static boolean schedulerEnabled = true;
  public static Duration schedulerInterval = Duration.parse("PT15S");
  public static Duration upcomingWindow = Duration.parse("PT15M");
  public static Duration endingSoonWindow = Duration.parse("PT10M");
  public static boolean wsEnabled = true;
  public static int streamMaxConnectionsPerUser = 1;

  // Iteration 6 operability
  public static boolean metricsEnabled = true;
  public static String logsLevel = "info";
  public static String userHashSalt = "changeme";
  public static String maxFileSize = "3MB";
  public static Duration maintenanceInterval = Duration.parse("PT30M");
  public static int backpressureQueueMax = 10000;
  public static int evaluatorQueueCutoff = 8000;

  @PostConstruct
  void init() {
    Config cfg = ConfigProvider.getConfig();
    enabled = cfg.getOptionalValue("notifications.enabled", Boolean.class).orElse(enabled);
    userCap = cfg.getOptionalValue("notifications.user-cap", Integer.class).orElse(userCap);
    globalCap = cfg.getOptionalValue("notifications.global-cap", Integer.class).orElse(globalCap);
    flushInterval =
        cfg.getOptionalValue("notifications.flush-interval", Duration.class).orElse(flushInterval);
    retentionDays =
        cfg.getOptionalValue("notifications.retention-days", Integer.class).orElse(retentionDays);
    maxQueueSize =
        cfg.getOptionalValue("notifications.max-queue-size", Integer.class).orElse(maxQueueSize);
    dedupeWindow =
        cfg.getOptionalValue("notifications.dedupe-window", Duration.class).orElse(dedupeWindow);
    dropOnQueueFull =
        cfg.getOptionalValue("notifications.drop-on-queue-full", Boolean.class)
            .orElse(dropOnQueueFull);
    schedulerEnabled =
        cfg.getOptionalValue("notifications.scheduler.enabled", Boolean.class)
            .orElse(schedulerEnabled);
    schedulerInterval =
        cfg.getOptionalValue("notifications.scheduler.interval", Duration.class)
            .orElse(schedulerInterval);
    upcomingWindow =
        cfg.getOptionalValue("notifications.upcoming.window", Duration.class)
            .orElse(upcomingWindow);
    endingSoonWindow =
        cfg.getOptionalValue("notifications.endingSoon.window", Duration.class)
            .orElse(endingSoonWindow);
    wsEnabled = cfg.getOptionalValue("notifications.ws.enabled", Boolean.class).orElse(wsEnabled);
    streamMaxConnectionsPerUser =
        cfg.getOptionalValue("notifications.stream.maxConnectionsPerUser", Integer.class)
            .orElse(streamMaxConnectionsPerUser);
    metricsEnabled =
        cfg.getOptionalValue("notifications.metrics.enabled", Boolean.class).orElse(metricsEnabled);
    logsLevel = cfg.getOptionalValue("notifications.logs.level", String.class).orElse(logsLevel);
    String profile = cfg.getOptionalValue("quarkus.profile", String.class).orElse("prod");
    userHashSalt = resolveUserHashSalt(cfg, profile);
    maxFileSize =
        cfg.getOptionalValue("notifications.max-file-size", String.class).orElse(maxFileSize);
    maintenanceInterval =
        cfg.getOptionalValue("notifications.maintenance.interval", Duration.class)
            .orElse(maintenanceInterval);
    backpressureQueueMax =
        cfg.getOptionalValue("notifications.backpressure.queue.max", Integer.class)
            .orElse(backpressureQueueMax);
    evaluatorQueueCutoff =
        cfg.getOptionalValue(
                "notifications.backpressure.cutoff.evaluator-queue-depth", Integer.class)
            .orElse(evaluatorQueueCutoff);
  }

  private String resolveUserHashSalt(Config cfg, String profile) {
    boolean devProfile = profile.startsWith("dev") || profile.startsWith("test");
    String configured =
        cfg.getOptionalValue("notifications.user-hash.salt", String.class).orElse(userHashSalt);
    if (configured != null) {
      configured = configured.trim();
    }

    if (configured == null || configured.isEmpty() || "changeme".equalsIgnoreCase(configured)) {
      if (devProfile) {
        String generated = generateRandomSalt();
        LOG.warn(
            "notifications.user-hash.salt is not set or insecure; generating a random dev/test salt");
        return generated;
      }
      throw new IllegalStateException(
          "notifications.user-hash.salt must be set to a strong secret (min 16 chars)");
    }

    if (configured.length() < MIN_SALT_LENGTH) {
      if (devProfile) {
        LOG.warnf(
            "notifications.user-hash.salt length is weak (%d). Use at least %d chars in dev/test.",
            configured.length(), MIN_SALT_LENGTH);
      } else {
        throw new IllegalStateException(
            String.format(
                "notifications.user-hash.salt too short (%d). Use at least %d characters.",
                configured.length(), MIN_SALT_LENGTH));
      }
    }
    return configured;
  }

  private String generateRandomSalt() {
    byte[] bytes = new byte[RANDOM_SALT_BYTES];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
