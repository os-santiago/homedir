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
    @JsonProperty("pilot_live_channel_updated_by") String pilotLiveChannelUpdatedBy,
    @JsonProperty("pilot_live_armed") boolean pilotLiveArmed,
    @JsonProperty("pilot_live_armed_at") Instant pilotLiveArmedAt,
    @JsonProperty("pilot_live_armed_by") String pilotLiveArmedBy,
    @JsonProperty("pilot_verification_acknowledged") boolean pilotVerificationAcknowledged,
    @JsonProperty("pilot_verification_acknowledged_at") Instant pilotVerificationAcknowledgedAt,
    @JsonProperty("pilot_verification_acknowledged_by") String pilotVerificationAcknowledgedBy,
    @JsonProperty("pilot_decision") String pilotDecision,
    @JsonProperty("pilot_decision_at") Instant pilotDecisionAt,
    @JsonProperty("pilot_decision_by") String pilotDecisionBy) {

  public static final int SCHEMA_VERSION = 3;

  public CampaignOperationsStateSnapshot {
    updatedBy = updatedBy == null ? "" : updatedBy.trim();
    channelAutomation = channelAutomation == null ? Map.of() : Map.copyOf(channelAutomation);
    channelGoLiveAcknowledgements =
        channelGoLiveAcknowledgements == null ? Map.of() : Map.copyOf(channelGoLiveAcknowledgements);
    pilotLiveChannel = pilotLiveChannel == null ? "" : pilotLiveChannel.trim().toLowerCase();
    pilotLiveChannelUpdatedBy = pilotLiveChannelUpdatedBy == null ? "" : pilotLiveChannelUpdatedBy.trim();
    pilotLiveArmedBy = pilotLiveArmedBy == null ? "" : pilotLiveArmedBy.trim();
    pilotVerificationAcknowledgedBy =
        pilotVerificationAcknowledgedBy == null ? "" : pilotVerificationAcknowledgedBy.trim();
    pilotDecision = pilotDecision == null ? "" : pilotDecision.trim().toLowerCase();
    pilotDecisionBy = pilotDecisionBy == null ? "" : pilotDecisionBy.trim();
  }

  public static CampaignOperationsStateSnapshot empty() {
    return new CampaignOperationsStateSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        "system",
        true,
        true,
        Map.of(),
        Map.of(),
        "",
        null,
        "",
        false,
        null,
        "",
        false,
        null,
        "",
        "",
        null,
        "");
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

  public boolean isPilotLiveActive(String channel) {
    return pilotLiveArmed && isPilotLiveChannel(channel);
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
        pilotLiveChannelUpdatedBy,
        pilotLiveArmed,
        pilotLiveArmedAt,
        pilotLiveArmedBy,
        pilotVerificationAcknowledged,
        pilotVerificationAcknowledgedAt,
        pilotVerificationAcknowledgedBy,
        pilotDecision,
        pilotDecisionAt,
        pilotDecisionBy);
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
        pilotLiveChannelUpdatedBy,
        pilotLiveArmed,
        pilotLiveArmedAt,
        pilotLiveArmedBy,
        pilotVerificationAcknowledged,
        pilotVerificationAcknowledgedAt,
        pilotVerificationAcknowledgedBy,
        pilotDecision,
        pilotDecisionAt,
        pilotDecisionBy);
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
        pilotLiveChannelUpdatedBy,
        pilotLiveArmed,
        pilotLiveArmedAt,
        pilotLiveArmedBy,
        pilotVerificationAcknowledged,
        pilotVerificationAcknowledgedAt,
        pilotVerificationAcknowledgedBy,
        pilotDecision,
        pilotDecisionAt,
        pilotDecisionBy);
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
        pilotLiveChannelUpdatedBy,
        pilotLiveArmed,
        pilotLiveArmedAt,
        pilotLiveArmedBy,
        pilotVerificationAcknowledged,
        pilotVerificationAcknowledgedAt,
        pilotVerificationAcknowledgedBy,
        pilotDecision,
        pilotDecisionAt,
        pilotDecisionBy);
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
        normalized.isBlank() ? "" : (actor == null ? "" : actor.trim()),
        false,
        null,
        "",
        false,
        null,
        "",
        "",
        null,
        "");
  }

  public CampaignOperationsStateSnapshot withPilotLiveArmed(boolean armed, String actor) {
    return new CampaignOperationsStateSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        actor == null ? "" : actor.trim(),
        refreshAutomationEnabled,
        publishAutomationEnabled,
        channelAutomation,
        channelGoLiveAcknowledgements,
        pilotLiveChannel,
        pilotLiveChannelUpdatedAt,
        pilotLiveChannelUpdatedBy,
        armed && !pilotLiveChannel.isBlank(),
        armed && !pilotLiveChannel.isBlank() ? Instant.now() : null,
        armed && !pilotLiveChannel.isBlank() ? (actor == null ? "" : actor.trim()) : "",
        false,
        null,
        "",
        "",
        null,
        "");
  }

  public CampaignOperationsStateSnapshot withPilotVerificationAcknowledged(
      boolean acknowledged, String actor) {
    return new CampaignOperationsStateSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        actor == null ? "" : actor.trim(),
        refreshAutomationEnabled,
        publishAutomationEnabled,
        channelAutomation,
        channelGoLiveAcknowledgements,
        pilotLiveChannel,
        pilotLiveChannelUpdatedAt,
        pilotLiveChannelUpdatedBy,
        pilotLiveArmed,
        pilotLiveArmedAt,
        pilotLiveArmedBy,
        acknowledged && !pilotLiveChannel.isBlank() && pilotLiveArmed,
        acknowledged && !pilotLiveChannel.isBlank() && pilotLiveArmed ? Instant.now() : null,
        acknowledged && !pilotLiveChannel.isBlank() && pilotLiveArmed ? (actor == null ? "" : actor.trim()) : "",
        "",
        null,
        "");
  }

  public CampaignOperationsStateSnapshot withPilotDecision(String decision, String actor) {
    String normalized = decision == null ? "" : decision.trim().toLowerCase();
    if (!"approved".equals(normalized) && !"hold".equals(normalized)) {
      normalized = "";
    }
    boolean eligible = !pilotLiveChannel.isBlank() && pilotLiveArmed && pilotVerificationAcknowledged;
    return new CampaignOperationsStateSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        actor == null ? "" : actor.trim(),
        refreshAutomationEnabled,
        publishAutomationEnabled,
        channelAutomation,
        channelGoLiveAcknowledgements,
        pilotLiveChannel,
        pilotLiveChannelUpdatedAt,
        pilotLiveChannelUpdatedBy,
        pilotLiveArmed,
        pilotLiveArmedAt,
        pilotLiveArmedBy,
        pilotVerificationAcknowledged,
        pilotVerificationAcknowledgedAt,
        pilotVerificationAcknowledgedBy,
        eligible ? normalized : "",
        eligible && !normalized.isBlank() ? Instant.now() : null,
        eligible && !normalized.isBlank() ? (actor == null ? "" : actor.trim()) : "");
  }
}
