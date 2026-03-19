package com.scanales.homedir.volunteers;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record VolunteerEventConfig(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("accepting_submissions") boolean acceptingSubmissions,
    @JsonProperty("opens_at") Instant opensAt,
    @JsonProperty("closes_at") Instant closesAt,
    @JsonProperty("updated_at") Instant updatedAt) {

  public static VolunteerEventConfig defaults(String eventId) {
    return new VolunteerEventConfig(eventId, true, null, null, Instant.now());
  }
}