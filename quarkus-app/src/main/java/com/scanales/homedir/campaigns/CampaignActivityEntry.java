package com.scanales.homedir.campaigns;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record CampaignActivityEntry(
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("draft_id") String draftId,
    @JsonProperty("kind") String kind,
    @JsonProperty("workflow_state") String workflowState,
    @JsonProperty("event_code") String eventCode,
    @JsonProperty("channel") String channel,
    @JsonProperty("outcome") String outcome,
    @JsonProperty("actor") String actor) {

  public CampaignActivityEntry {
    draftId = draftId == null ? "" : draftId.trim();
    kind = kind == null ? "" : kind.trim();
    workflowState = workflowState == null ? "" : workflowState.trim();
    eventCode = eventCode == null ? "" : eventCode.trim();
    channel = channel == null ? "" : channel.trim();
    outcome = outcome == null ? "" : outcome.trim();
    actor = actor == null ? "" : actor.trim();
  }
}
