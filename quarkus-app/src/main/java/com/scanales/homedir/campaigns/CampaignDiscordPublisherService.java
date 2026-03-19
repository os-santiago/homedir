package com.scanales.homedir.campaigns;

import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CampaignDiscordPublisherService {
  private static final Logger LOG = Logger.getLogger(CampaignDiscordPublisherService.class);
  private static final String CHANNEL = "discord";

  @ConfigProperty(name = "campaigns.publish.enabled", defaultValue = "false")
  boolean publishEnabled;

  @ConfigProperty(name = "campaigns.publish.dry-run", defaultValue = "true")
  boolean dryRun;

  @ConfigProperty(name = "campaigns.publish.discord.enabled", defaultValue = "false")
  boolean discordEnabled;

  @ConfigProperty(name = "campaigns.publish.discord.webhook-url")
  Optional<String> webhookUrl;

  @ConfigProperty(name = "campaigns.publish.discord.timeout", defaultValue = "PT5S")
  Duration timeout;

  @ConfigProperty(name = "campaigns.publish.discord.min-interval", defaultValue = "PT15M")
  Duration minInterval;

  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public CampaignPublisherStatus status() {
    return new CampaignPublisherStatus(
        CHANNEL,
        publishEnabled,
        dryRun,
        discordEnabled,
        webhookUrl.isPresent() && !webhookUrl.get().isBlank(),
        effectiveMinInterval());
  }

  public CampaignPublishResult publish(CampaignDraftState draft) {
    CampaignPublisherStatus status = status();
    if (!status.globalEnabled()) {
      return CampaignPublishResult.skipped(CHANNEL, "global_disabled");
    }
    if (!status.channelEnabled()) {
      return CampaignPublishResult.skipped(CHANNEL, "discord_disabled");
    }
    if (!status.configured()) {
      return CampaignPublishResult.skipped(CHANNEL, "discord_not_configured");
    }
    if (status.dryRun()) {
      LOG.info("campaigns_discord_publish_dry_run");
      return CampaignPublishResult.skipped(CHANNEL, "dry_run");
    }

    try {
      String payload =
          "{\"content\":\""
              + CampaignPublishMessageSupport.escapeJson(CampaignPublishMessageSupport.messageFor(draft, CHANNEL))
              + "\"}";
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(webhookUrl.orElseThrow().trim()))
              .timeout(effectiveTimeout())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return CampaignPublishResult.published(CHANNEL, Instant.now(), "published");
      }
      LOG.warnf("campaigns_discord_publish_failed status=%d", response.statusCode());
      return CampaignPublishResult.failed(CHANNEL, "discord_failed");
    } catch (Exception e) {
      LOG.warn("campaigns_discord_publish_error");
      return CampaignPublishResult.failed(CHANNEL, "discord_error");
    }
  }

  private Duration effectiveTimeout() {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      return Duration.ofSeconds(5);
    }
    return timeout;
  }

  public Duration effectiveMinInterval() {
    if (minInterval == null || minInterval.isZero() || minInterval.isNegative()) {
      return Duration.ofMinutes(15);
    }
    return minInterval;
  }
}
