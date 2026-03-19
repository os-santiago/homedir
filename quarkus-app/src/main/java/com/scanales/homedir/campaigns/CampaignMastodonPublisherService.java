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
public class CampaignMastodonPublisherService {
  private static final Logger LOG = Logger.getLogger(CampaignMastodonPublisherService.class);
  private static final String CHANNEL = "mastodon";

  @ConfigProperty(name = "campaigns.publish.enabled", defaultValue = "false")
  boolean publishEnabled;

  @ConfigProperty(name = "campaigns.publish.dry-run", defaultValue = "true")
  boolean dryRun;

  @ConfigProperty(name = "campaigns.publish.mastodon.enabled", defaultValue = "false")
  boolean mastodonEnabled;

  @ConfigProperty(name = "campaigns.publish.mastodon.base-url")
  Optional<String> baseUrl;

  @ConfigProperty(name = "campaigns.publish.mastodon.access-token")
  Optional<String> accessToken;

  @ConfigProperty(name = "campaigns.publish.mastodon.timeout", defaultValue = "PT5S")
  Duration timeout;

  @ConfigProperty(name = "campaigns.publish.mastodon.min-interval", defaultValue = "PT15M")
  Duration minInterval;

  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public CampaignPublisherStatus status() {
    return new CampaignPublisherStatus(
        CHANNEL,
        publishEnabled,
        dryRun,
        mastodonEnabled,
        baseUrl.isPresent()
            && !baseUrl.orElse("").isBlank()
            && accessToken.isPresent()
            && !accessToken.orElse("").isBlank(),
        effectiveMinInterval());
  }

  public CampaignPublishResult publish(CampaignDraftState draft) {
    CampaignPublisherStatus status = status();
    if (!status.globalEnabled()) {
      return CampaignPublishResult.skipped(CHANNEL, "global_disabled");
    }
    if (!status.channelEnabled()) {
      return CampaignPublishResult.skipped(CHANNEL, "mastodon_disabled");
    }
    if (!status.configured()) {
      return CampaignPublishResult.skipped(CHANNEL, "mastodon_not_configured");
    }
    if (status.dryRun()) {
      LOG.info("campaigns_mastodon_publish_dry_run");
      return CampaignPublishResult.skipped(CHANNEL, "dry_run");
    }

    try {
      String endpoint =
          CampaignPublishMessageSupport.normalizeBaseUrl(baseUrl.orElse(""), "");
      String message =
          CampaignPublishMessageSupport.truncate(
              CampaignPublishMessageSupport.messageFor(draft), 450);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(endpoint + "/api/v1/statuses"))
              .timeout(effectiveTimeout())
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + accessToken.orElseThrow().trim())
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      "{\"status\":\""
                          + CampaignPublishMessageSupport.escapeJson(message)
                          + "\"}",
                      StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return CampaignPublishResult.published(CHANNEL, Instant.now(), "published_mastodon");
      }
      LOG.warnf("campaigns_mastodon_publish_failed status=%d", response.statusCode());
      return CampaignPublishResult.failed(CHANNEL, "mastodon_failed");
    } catch (Exception e) {
      LOG.warn("campaigns_mastodon_publish_error");
      return CampaignPublishResult.failed(CHANNEL, "mastodon_error");
    }
  }

  public Duration effectiveMinInterval() {
    if (minInterval == null || minInterval.isZero() || minInterval.isNegative()) {
      return Duration.ofMinutes(15);
    }
    return minInterval;
  }

  private Duration effectiveTimeout() {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      return Duration.ofSeconds(5);
    }
    return timeout;
  }
}
