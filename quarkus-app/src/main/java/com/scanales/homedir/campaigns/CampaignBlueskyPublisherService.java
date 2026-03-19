package com.scanales.homedir.campaigns;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
public class CampaignBlueskyPublisherService {
  private static final Logger LOG = Logger.getLogger(CampaignBlueskyPublisherService.class);
  private static final String CHANNEL = "bluesky";

  @ConfigProperty(name = "campaigns.publish.enabled", defaultValue = "false")
  boolean publishEnabled;

  @ConfigProperty(name = "campaigns.publish.dry-run", defaultValue = "true")
  boolean dryRun;

  @ConfigProperty(name = "campaigns.publish.bluesky.enabled", defaultValue = "false")
  boolean blueskyEnabled;

  @ConfigProperty(name = "campaigns.publish.bluesky.handle")
  Optional<String> handle;

  @ConfigProperty(name = "campaigns.publish.bluesky.app-password")
  Optional<String> appPassword;

  @ConfigProperty(name = "campaigns.publish.bluesky.service-endpoint", defaultValue = "https://bsky.social")
  String serviceEndpoint;

  @ConfigProperty(name = "campaigns.publish.bluesky.timeout", defaultValue = "PT5S")
  Duration timeout;

  @ConfigProperty(name = "campaigns.publish.bluesky.min-interval", defaultValue = "PT15M")
  Duration minInterval;

  @Inject ObjectMapper objectMapper;

  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public CampaignPublisherStatus status() {
    return new CampaignPublisherStatus(
        CHANNEL,
        publishEnabled,
        dryRun,
        blueskyEnabled,
        handle.isPresent()
            && !handle.orElse("").isBlank()
            && appPassword.isPresent()
            && !appPassword.orElse("").isBlank(),
        effectiveMinInterval());
  }

  public CampaignPublishResult publish(CampaignDraftState draft) {
    CampaignPublisherStatus status = status();
    if (!status.globalEnabled()) {
      return CampaignPublishResult.skipped(CHANNEL, "global_disabled");
    }
    if (!status.channelEnabled()) {
      return CampaignPublishResult.skipped(CHANNEL, "bluesky_disabled");
    }
    if (!status.configured()) {
      return CampaignPublishResult.skipped(CHANNEL, "bluesky_not_configured");
    }
    if (status.dryRun()) {
      LOG.info("campaigns_bluesky_publish_dry_run");
      return CampaignPublishResult.skipped(CHANNEL, "dry_run");
    }

    try {
      String endpoint =
          CampaignPublishMessageSupport.normalizeBaseUrl(serviceEndpoint, "https://bsky.social");
      HttpRequest sessionRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(endpoint + "/xrpc/com.atproto.server.createSession"))
              .timeout(effectiveTimeout())
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      "{\"identifier\":\""
                          + CampaignPublishMessageSupport.escapeJson(handle.orElseThrow().trim())
                          + "\",\"password\":\""
                          + CampaignPublishMessageSupport.escapeJson(appPassword.orElseThrow().trim())
                          + "\"}",
                      StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> sessionResponse =
          httpClient.send(sessionRequest, HttpResponse.BodyHandlers.ofString());
      if (sessionResponse.statusCode() < 200 || sessionResponse.statusCode() >= 300) {
        LOG.warnf("campaigns_bluesky_session_failed status=%d", sessionResponse.statusCode());
        return CampaignPublishResult.failed(CHANNEL, "bluesky_failed");
      }

      JsonNode sessionJson = objectMapper.readTree(sessionResponse.body());
      String accessJwt = CampaignPublishMessageSupport.safe(sessionJson.path("accessJwt").asText());
      String did = CampaignPublishMessageSupport.safe(sessionJson.path("did").asText());
      if (accessJwt.isBlank() || did.isBlank()) {
        return CampaignPublishResult.failed(CHANNEL, "bluesky_failed");
      }

      String message =
          CampaignPublishMessageSupport.truncate(
              CampaignPublishMessageSupport.messageFor(draft, CHANNEL), 280);
      String recordJson =
          "{\"repo\":\""
              + CampaignPublishMessageSupport.escapeJson(did)
              + "\",\"collection\":\"app.bsky.feed.post\",\"record\":{\"text\":\""
              + CampaignPublishMessageSupport.escapeJson(message)
              + "\",\"createdAt\":\""
              + Instant.now().toString()
              + "\"}}";
      HttpRequest publishRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(endpoint + "/xrpc/com.atproto.repo.createRecord"))
              .timeout(effectiveTimeout())
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + accessJwt)
              .POST(HttpRequest.BodyPublishers.ofString(recordJson, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> publishResponse =
          httpClient.send(publishRequest, HttpResponse.BodyHandlers.ofString());
      if (publishResponse.statusCode() >= 200 && publishResponse.statusCode() < 300) {
        return CampaignPublishResult.published(CHANNEL, Instant.now(), "published_bluesky");
      }
      LOG.warnf("campaigns_bluesky_publish_failed status=%d", publishResponse.statusCode());
      return CampaignPublishResult.failed(CHANNEL, "bluesky_failed");
    } catch (Exception e) {
      LOG.warn("campaigns_bluesky_publish_error");
      return CampaignPublishResult.failed(CHANNEL, "bluesky_error");
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
