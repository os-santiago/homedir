package com.scanales.homedir.eventops;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record EventSpace(
    String id,
    @JsonProperty("event_id") String eventId,
    String name,
    String type,
    Integer capacity,
    boolean active,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt) {}

