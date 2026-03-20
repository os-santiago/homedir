package com.scanales.homedir.campaigns;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record CampaignGoLiveAck(
    @JsonProperty("acknowledged") boolean acknowledged,
    @JsonProperty("acknowledged_at") Instant acknowledgedAt,
    @JsonProperty("acknowledged_by") String acknowledgedBy) {

  public CampaignGoLiveAck {
    acknowledgedBy = acknowledgedBy == null ? "" : acknowledgedBy.trim();
  }

  public static CampaignGoLiveAck empty() {
    return new CampaignGoLiveAck(false, null, "");
  }

  public CampaignGoLiveAck withAcknowledged(boolean nextAcknowledged, String actor) {
    return new CampaignGoLiveAck(
        nextAcknowledged,
        nextAcknowledged ? Instant.now() : null,
        nextAcknowledged ? (actor == null ? "" : actor.trim()) : "");
  }
}
