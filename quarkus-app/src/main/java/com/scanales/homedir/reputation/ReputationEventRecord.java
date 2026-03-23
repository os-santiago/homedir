package com.scanales.homedir.reputation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Locale;

public record ReputationEventRecord(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("actor_user_id") String actorUserId,
    @JsonProperty("event_type") String eventType,
    @JsonProperty("event_category") String eventCategory,
    ReputationDimension dimension,
    @JsonProperty("weight_base") int weightBase,
    @JsonProperty("source_object_type") String sourceObjectType,
    @JsonProperty("source_object_id") String sourceObjectId,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("validated_by_user_id") String validatedByUserId,
    @JsonProperty("validation_type") String validationType,
    @JsonProperty("scope_type") String scopeType,
    @JsonProperty("scope_id") String scopeId) {

  public ReputationEventRecord {
    eventId = sanitizeToken(eventId);
    actorUserId = sanitizeUser(actorUserId);
    eventType = sanitizeToken(eventType);
    eventCategory = sanitizeToken(eventCategory);
    dimension = dimension == null ? ReputationDimension.PARTICIPATION : dimension;
    weightBase = Math.max(0, weightBase);
    sourceObjectType = sanitizeToken(sourceObjectType);
    sourceObjectId = sanitizeValue(sourceObjectId);
    createdAt = createdAt == null ? Instant.now() : createdAt;
    validatedByUserId = sanitizeUser(validatedByUserId);
    validationType = sanitizeToken(validationType);
    scopeType = sanitizeToken(scopeType);
    scopeId = sanitizeValue(scopeId);
  }

  private static String sanitizeUser(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  private static String sanitizeToken(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]", "_");
    return normalized.isBlank() ? null : normalized;
  }

  private static String sanitizeValue(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim();
  }
}
