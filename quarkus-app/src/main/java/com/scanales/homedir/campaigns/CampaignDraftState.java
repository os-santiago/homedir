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
    boolean sourceAvailable) {

  public CampaignDraftState {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    suggestedChannels = suggestedChannels == null ? List.of() : List.copyOf(suggestedChannels);
    workflowState = workflowState == null ? CampaignWorkflowState.DRAFT : workflowState;
    approvedBy = approvedBy == null ? "" : approvedBy.trim();
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
        nextSourceAvailable);
  }
}
