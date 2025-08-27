package com.scanales.eventflow.notifications;

import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;

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
}
