package com.scanales.eventflow.community;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CommunityLightningDiscordAlertService {
  private static final Logger LOG = Logger.getLogger(CommunityLightningDiscordAlertService.class);

  @ConfigProperty(name = "community.lightning.alerts.discord.enabled", defaultValue = "false")
  boolean enabled;

  @ConfigProperty(name = "community.lightning.alerts.discord.webhook-url")
  Optional<String> webhookUrl;

  @ConfigProperty(name = "community.lightning.alerts.discord.timeout", defaultValue = "PT5S")
  Duration timeout;

  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "community-lightning-discord-alert");
            thread.setDaemon(true);
            return thread;
          });

  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public void sendReportAlertAsync(CommunityLightningReport report, String summary) {
    if (!isConfigured()) {
      return;
    }
    executor.submit(() -> sendReportAlert(report, summary));
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }

  private boolean isConfigured() {
    return enabled && webhookUrl.isPresent() && !webhookUrl.get().isBlank();
  }

  private void sendReportAlert(CommunityLightningReport report, String summary) {
    try {
      String safeSummary =
          summary == null || summary.isBlank() ? "Community report submitted." : summary.trim();
      String content =
          "**HomeDir Social Report**\n"
              + "- Target: "
              + safe(report.targetType())
              + " `"
              + safe(report.targetId())
              + "`\n"
              + "- Reporter: "
              + safe(report.userName())
              + "\n"
              + "- Reason: "
              + safe(report.reason())
              + "\n"
              + "- Context: "
              + safeSummary;
      String payload = "{\"content\":\"" + escapeJson(content) + "\"}";

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(webhookUrl.get().trim()))
              .timeout(effectiveTimeout())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        LOG.warnf("community_lightning_discord_alert_failed status=%d", response.statusCode());
      }
    } catch (Exception e) {
      LOG.warnf("community_lightning_discord_alert_error %s", e.getMessage());
    }
  }

  private Duration effectiveTimeout() {
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      return Duration.ofSeconds(5);
    }
    return timeout;
  }

  private static String safe(String value) {
    if (value == null || value.isBlank()) {
      return "n/a";
    }
    return value.trim();
  }

  private static String escapeJson(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "");
  }
}
