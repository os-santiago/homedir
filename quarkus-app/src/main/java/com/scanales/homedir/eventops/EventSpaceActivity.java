package com.scanales.homedir.eventops;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record EventSpaceActivity(
    String id,
    @JsonProperty("event_id") String eventId,
    @JsonProperty("space_id") String spaceId,
    String title,
    String details,
    String visibility,
    @JsonProperty("start_at") Instant startAt,
    @JsonProperty("end_at") Instant endAt,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt) {}

