package io.eventflow.notifications.global;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.time.Duration;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/** Configuration for global notifications. */
@Singleton
public class GlobalNotificationConfig {
  public static boolean enabled = true;
  public static int bufferSize = 1000;
  public static Duration dedupeWindow = Duration.parse("PT10M");

  @PostConstruct
  void init() {
    Config cfg = ConfigProvider.getConfig();
    enabled = cfg.getOptionalValue("notifications.global.enabled", Boolean.class).orElse(enabled);
    bufferSize = cfg.getOptionalValue("notifications.global.buffer-size", Integer.class).orElse(bufferSize);
    dedupeWindow =
        cfg.getOptionalValue("notifications.global.dedupe-window", Duration.class).orElse(dedupeWindow);
  }
}
