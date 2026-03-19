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
    boolean approvalRequired) {

  public CampaignDraftState {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    suggestedChannels = suggestedChannels == null ? List.of() : List.copyOf(suggestedChannels);
  }
}
