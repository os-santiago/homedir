package com.scanales.homedir.eventops;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record EventStaffAssignment(
    String id,
    @JsonProperty("event_id") String eventId,
    @JsonProperty("user_id") String userId,
    @JsonProperty("user_name") String userName,
    String role,
    String source,
    boolean active,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt) {}
