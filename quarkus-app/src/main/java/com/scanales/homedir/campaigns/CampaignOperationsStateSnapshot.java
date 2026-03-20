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
    @JsonProperty("channel_automation") Map<String, Boolean> channelAutomation,
    @JsonProperty("channel_go_live_acknowledgements")
        Map<String, CampaignGoLiveAck> channelGoLiveAcknowledgements,
    @JsonProperty("pilot_live_channel") String pilotLiveChannel,
    @JsonProperty("pilot_live_channel_updated_at") Instant pilotLiveChannelUpdatedAt,
    @JsonProperty("pilot_live_channel_updated_by") String pilotLiveChannelUpdatedBy) {

  public static final int SCHEMA_VERSION = 1;

  public CampaignOperationsStateSnapshot {
    updatedBy = updatedBy == null ? "" : updatedBy.trim();
    channelAutomation = channelAutomation == null ? Map.of() : Map.copyOf(channelAutomation);
    channelGoLiveAcknowledgements =
        channelGoLiveAcknowledgements == null ? Map.of() : Map.copyOf(channelGoLiveAcknowledgements);
    pilotLiveChannel = pilotLiveChannel == null ? "" : pilotLiveChannel.trim().toLowerCase();
    pilotLiveChannelUpdatedBy = pilotLiveChannelUpdatedBy == null ? "" : pilotLiveChannelUpdatedBy.trim();
  }

  public static CampaignOperationsStateSnapshot empty() {
    return new CampaignOperationsStateSnapshot(
        SCHEMA_VERSION, Instant.now(), "system", true, true, Map.of(), Map.of(), "", null, "");
  }

  public boolean isChannelAutomationEnabled(String channel) {
    if (channel == null || channel.isBlank()) {
      return true;
    }
    return channelAutomation.getOrDefault(channel.trim().toLowerCase(), true);
  }

  public CampaignGoLiveAck goLiveAcknowledgement(String channel) {
    if (channel == null || channel.isBlank()) {
      return CampaignGoLiveAck.empty();
    }
    return channelGoLiveAcknowledgements.getOrDefault(channel.trim().toLowerCase(), CampaignGoLiveAck.empty());
  }

  public boolean hasPilotLiveChannel() {
    return !pilotLiveChannel.isBlank();
  }

  public boolean isPilotLiveChannel(String channel) {
    if (!hasPilotLiveChannel() || channel == null || channel.isBlank()) {
      return !hasPilotLiveChannel();
    }
    return pilotLiveChannel.equals(channel.trim().toLowerCase());
  }

  public CampaignOperationsStateSnapshot withRefreshAutomation(boolean enabled, String actor) {
    return new CampaignOperationsStateSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        actor == null ? "" : actor.trim(),
        enabled,
        publishAutomationEnabled,
        channelAutomation,
        channelGoLiveAcknowledgements,
        pilotLiveChannel,
        pilotLiveChannelUpdatedAt,
        pilotLiveChannelUpdatedBy);
  }

  public CampaignOperationsStateSnapshot withPublishAutomation(boolean enabled, String actor) {
    return new CampaignOperationsStateSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        actor == null ? "" : actor.trim(),
        refreshAutomationEnabled,
        enabled,
        channelAutomation,
        channelGoLiveAcknowledgements,
        pilotLiveChannel,
        pilotLiveChannelUpdatedAt,
        pilotLiveChannelUpdatedBy);
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
        next,
        channelGoLiveAcknowledgements,
        pilotLiveChannel,
        pilotLiveChannelUpdatedAt,
        pilotLiveChannelUpdatedBy);
  }

  public CampaignOperationsStateSnapshot withChannelGoLiveAcknowledgement(
      String channel, boolean acknowledged, String actor) {
    java.util.LinkedHashMap<String, CampaignGoLiveAck> next =
        new java.util.LinkedHashMap<>(channelGoLiveAcknowledgements);
    if (channel != null && !channel.isBlank()) {
      next.put(channel.trim().toLowerCase(), CampaignGoLiveAck.empty().withAcknowledged(acknowledged, actor));
    }
    return new CampaignOperationsStateSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        actor == null ? "" : actor.trim(),
        refreshAutomationEnabled,
        publishAutomationEnabled,
        channelAutomation,
        next,
        pilotLiveChannel,
        pilotLiveChannelUpdatedAt,
        pilotLiveChannelUpdatedBy);
  }

  public CampaignOperationsStateSnapshot withPilotLiveChannel(String channel, String actor) {
    String normalized = channel == null ? "" : channel.trim().toLowerCase();
    return new CampaignOperationsStateSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        actor == null ? "" : actor.trim(),
        refreshAutomationEnabled,
        publishAutomationEnabled,
        channelAutomation,
        channelGoLiveAcknowledgements,
        normalized,
        normalized.isBlank() ? null : Instant.now(),
        normalized.isBlank() ? "" : (actor == null ? "" : actor.trim()));
  }
}
