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

  public DiscordPublisherStatus status() {
    return new DiscordPublisherStatus(
        publishEnabled,
        dryRun,
        discordEnabled,
        webhookUrl.isPresent() && !webhookUrl.get().isBlank(),
        effectiveMinInterval());
  }

  public PublishResult publish(CampaignDraftState draft) {
    DiscordPublisherStatus status = status();
    if (!status.globalEnabled()) {
      return PublishResult.skipped("global_disabled");
    }
    if (!status.channelEnabled()) {
      return PublishResult.skipped("discord_disabled");
    }
    if (!status.webhookConfigured()) {
      return PublishResult.skipped("discord_not_configured");
    }
    if (status.dryRun()) {
      LOG.info("campaigns_discord_publish_dry_run");
      return PublishResult.skipped("dry_run");
    }

    try {
      String payload = "{\"content\":\"" + escapeJson(messageFor(draft)) + "\"}";
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(webhookUrl.orElseThrow().trim()))
              .timeout(effectiveTimeout())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return PublishResult.published(CHANNEL, Instant.now(), "published");
      }
      LOG.warnf("campaigns_discord_publish_failed status=%d", response.statusCode());
      return PublishResult.failed("discord_failed");
    } catch (Exception e) {
      LOG.warn("campaigns_discord_publish_error");
      return PublishResult.failed("discord_error");
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

  private String messageFor(CampaignDraftState draft) {
    String title = safe(draft.metadata().get("eventTitle"));
    if (title.isBlank()) {
      title = safe(draft.metadata().get("title"));
    }
    if (title.isBlank()) {
      title = safe(draft.id());
    }
    String ctaUrl = safe(draft.metadata().get("eventUrl"));
    if (ctaUrl.isBlank()) {
      ctaUrl = "/about";
    }
    return "**HomeDir Campaign**\n"
        + title
        + "\n"
        + "Track the latest verified progress inside HomeDir.\n"
        + "https://homedir.opensourcesantiago.io"
        + ctaUrl;
  }

  private static String safe(String raw) {
    return raw == null ? "" : raw.trim();
  }

  private static String escapeJson(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "");
  }

  public record DiscordPublisherStatus(
      boolean globalEnabled,
      boolean dryRun,
      boolean channelEnabled,
      boolean webhookConfigured,
      Duration minInterval) {}

  public record PublishResult(
      boolean published,
      boolean skipped,
      String channel,
      Instant publishedAt,
      String outcome) {
    public static PublishResult published(String channel, Instant publishedAt, String outcome) {
      return new PublishResult(true, false, channel, publishedAt, outcome);
    }

    public static PublishResult skipped(String outcome) {
      return new PublishResult(false, true, CHANNEL, null, outcome);
    }

    public static PublishResult failed(String outcome) {
      return new PublishResult(false, false, CHANNEL, null, outcome);
    }
  }
}
