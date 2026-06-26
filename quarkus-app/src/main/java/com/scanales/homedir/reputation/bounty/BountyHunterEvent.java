package com.scanales.homedir.reputation.bounty;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Locale;

/**
 * Represents a Bounty Hunter scoring event. Tracks when points are awarded for issue creation or
 * resolution.
 */
public record BountyHunterEvent(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("user_id") String userId,
    @JsonProperty("event_type") BountyHunterEventType eventType,
    @JsonProperty("issue_number") String issueNumber,
    @JsonProperty("pr_number") String prNumber,
    @JsonProperty("points_awarded") long pointsAwarded,
    @JsonProperty("label") String label,
    @JsonProperty("validated_by_user_id") String validatedByUserId,
    @JsonProperty("timestamp") Instant timestamp) {

  public BountyHunterEvent {
    eventId = sanitizeToken(eventId);
    userId = normalizeUser(userId);
    eventType = eventType == null ? BountyHunterEventType.ISSUE_CREATED : eventType;
    issueNumber = sanitizeToken(issueNumber);
    prNumber = sanitizeToken(prNumber);
    pointsAwarded = Math.max(0L, pointsAwarded);
    label = sanitizeToken(label);
    validatedByUserId = normalizeUser(validatedByUserId);
    timestamp = timestamp == null ? Instant.now() : timestamp;
  }

  private static String normalizeUser(String raw) {
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
}
