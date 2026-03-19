package com.scanales.homedir.campaigns;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record CampaignStateSnapshot(
    @JsonProperty("schema_version") int schemaVersion,
    @JsonProperty("generated_at") Instant generatedAt,
    @JsonProperty("drafts") List<CampaignDraftState> drafts,
    @JsonProperty("activity") List<CampaignActivityEntry> activity) {

  public static final int SCHEMA_VERSION = 3;

  public CampaignStateSnapshot {
    drafts = drafts == null ? List.of() : List.copyOf(drafts);
    activity = activity == null ? List.of() : List.copyOf(activity);
  }

  public static CampaignStateSnapshot empty() {
    return new CampaignStateSnapshot(SCHEMA_VERSION, null, List.of(), List.of());
  }
}
