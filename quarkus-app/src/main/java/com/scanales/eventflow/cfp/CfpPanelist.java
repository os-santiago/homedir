package com.scanales.eventflow.cfp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record CfpPanelist(
    String id,
    String name,
    String email,
    @JsonProperty("user_id") String userId,
    String status,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt) {}

