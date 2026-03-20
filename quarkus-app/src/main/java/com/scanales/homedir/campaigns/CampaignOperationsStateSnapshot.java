package com.scanales.homedir.campaigns;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public record CampaignOperationsStateSnapshot(
    @JsonProperty("schema_version") int schemaVersion,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("updated_by") String updatedBy,
    @JsonProperty("refresh_automation_enabled") boolean refreshAutomationEnabled,
    @JsonProperty("publish_automation_enabled") boolean publishAutomationEnabled,
    @JsonProperty("channel_automation") Map<String, Boolean> channelAutomation) {

  public static final int SCHEMA_VERSION = 1;

  public CampaignOperationsStateSnapshot {
    updatedBy = updatedBy == null ? "" : updatedBy.trim();
    channelAutomation = channelAutomation == null ? Map.of() : Map.copyOf(channelAutomation);
  }

  public static CampaignOperationsStateSnapshot empty() {
    return new CampaignOperationsStateSnapshot(
        SCHEMA_VERSION, Instant.now(), "system", true, true, Map.of());
  }

  public boolean isChannelAutomationEnabled(String channel) {
    if (channel == null || channel.isBlank()) {
      return true;
    }
    return channelAutomation.getOrDefault(channel.trim().toLowerCase(), true);
  }

  public CampaignOperationsStateSnapshot withRefreshAutomation(boolean enabled, String actor) {
    return new CampaignOperationsStateSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        actor == null ? "" : actor.trim(),
        enabled,
        publishAutomationEnabled,
        channelAutomation);
  }

  public CampaignOperationsStateSnapshot withPublishAutomation(boolean enabled, String actor) {
    return new CampaignOperationsStateSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        actor == null ? "" : actor.trim(),
        refreshAutomationEnabled,
        enabled,
        channelAutomation);
  }

  public CampaignOperationsStateSnapshot withChannelAutomation(
      String channel, boolean enabled, String actor) {
    java.util.LinkedHashMap<String, Boolean> next = new java.util.LinkedHashMap<>(channelAutomation);
    if (channel != null && !channel.isBlank()) {
      next.put(channel.trim().toLowerCase(), enabled);
    }
    return new CampaignOperationsStateSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        actor == null ? "" : actor.trim(),
        refreshAutomationEnabled,
        publishAutomationEnabled,
        next);
  }
}
