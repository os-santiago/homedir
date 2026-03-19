package com.scanales.homedir.cfp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Event-scoped CFP configuration overrides. */
public record CfpEventConfig(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("accepting_submissions") boolean acceptingSubmissions,
    @JsonProperty("opens_at") Instant opensAt,
    @JsonProperty("closes_at") Instant closesAt,
    @JsonProperty("max_submissions_per_user_per_event") Integer maxSubmissionsPerUserPerEvent,
    @JsonProperty("testing_mode_enabled") Boolean testingModeEnabled,
    @JsonProperty("results_published_at") Instant resultsPublishedAt,
    @JsonProperty("results_published_by") String resultsPublishedBy,
    @JsonProperty("accepted_results_message") String acceptedResultsMessage,
    @JsonProperty("rejected_results_message") String rejectedResultsMessage,
    @JsonProperty("updated_at") Instant updatedAt) {

  public static CfpEventConfig defaults(String eventId) {
    return new CfpEventConfig(eventId, true, null, null, null, null, null, null, null, null, Instant.now());
  }
}
