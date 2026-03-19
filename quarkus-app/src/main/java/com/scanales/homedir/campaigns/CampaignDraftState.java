package com.scanales.homedir.campaigns;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CampaignDraftState(
    String id,
    String kind,
    Instant generatedAt,
    Map<String, String> metadata,
    List<String> suggestedChannels,
    boolean approvalRequired,
    CampaignWorkflowState workflowState,
    Instant approvedAt,
    String approvedBy,
    Instant scheduledFor,
    Instant updatedAt,
    boolean sourceAvailable,
    Map<String, Instant> publishedChannels,
    Instant lastPublishAttemptAt,
    String lastPublishOutcome) {

  public CampaignDraftState {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    suggestedChannels = suggestedChannels == null ? List.of() : List.copyOf(suggestedChannels);
    workflowState = workflowState == null ? CampaignWorkflowState.DRAFT : workflowState;
    approvedBy = approvedBy == null ? "" : approvedBy.trim();
    publishedChannels = publishedChannels == null ? Map.of() : Map.copyOf(publishedChannels);
    lastPublishOutcome = lastPublishOutcome == null ? "" : lastPublishOutcome.trim();
  }

  public CampaignDraftState withWorkflow(
      CampaignWorkflowState nextState,
      Instant nextApprovedAt,
      String nextApprovedBy,
      Instant nextScheduledFor,
      Instant nextUpdatedAt,
      boolean nextSourceAvailable) {
    return new CampaignDraftState(
        id,
        kind,
        generatedAt,
        metadata,
        suggestedChannels,
        approvalRequired,
        nextState,
        nextApprovedAt,
        nextApprovedBy,
        nextScheduledFor,
        nextUpdatedAt,
        nextSourceAvailable,
        publishedChannels,
        lastPublishAttemptAt,
        lastPublishOutcome);
  }

  public CampaignDraftState withPublishStatus(
      CampaignWorkflowState nextState,
      Map<String, Instant> nextPublishedChannels,
      Instant nextAttemptAt,
      String nextOutcome,
      Instant nextUpdatedAt) {
    return new CampaignDraftState(
        id,
        kind,
        generatedAt,
        metadata,
        suggestedChannels,
        approvalRequired,
        nextState,
        approvedAt,
        approvedBy,
        scheduledFor,
        nextUpdatedAt,
        sourceAvailable,
        nextPublishedChannels,
        nextAttemptAt,
        nextOutcome);
  }

  public CampaignDraftState withManualChannelPublished(
      String channel,
      Instant publishedAt,
      String outcome,
      Instant nextUpdatedAt,
      String actor) {
    Map<String, Instant> nextPublishedChannels = new java.util.LinkedHashMap<>(publishedChannels);
    nextPublishedChannels.put(channel, publishedAt);
    Instant nextApprovedAt = approvedAt == null ? publishedAt : approvedAt;
    String nextApprovedBy =
        (approvedBy == null || approvedBy.isBlank()) && actor != null ? actor.trim() : approvedBy;
    return new CampaignDraftState(
        id,
        kind,
        generatedAt,
        metadata,
        suggestedChannels,
        approvalRequired,
        CampaignWorkflowState.PUBLISHED,
        nextApprovedAt,
        nextApprovedBy,
        scheduledFor,
        nextUpdatedAt,
        sourceAvailable,
        nextPublishedChannels,
        publishedAt,
        outcome);
  }
}
