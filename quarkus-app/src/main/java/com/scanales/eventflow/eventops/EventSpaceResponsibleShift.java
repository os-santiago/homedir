package com.scanales.eventflow.eventops;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record EventSpaceResponsibleShift(
    String id,
    @JsonProperty("event_id") String eventId,
    @JsonProperty("space_id") String spaceId,
    @JsonProperty("user_id") String userId,
    @JsonProperty("user_name") String userName,
    @JsonProperty("start_at") Instant startAt,
    @JsonProperty("end_at") Instant endAt) {}
