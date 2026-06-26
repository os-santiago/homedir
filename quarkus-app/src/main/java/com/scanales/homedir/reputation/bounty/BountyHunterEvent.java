package com.scanales.homedir.reputation.bounty;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Locale;

/**
 * Audit log entry for a Bounty Hunter scoring event.
 * Records when points are awarded for issue creation or resolution.
 */
public record BountyHunterEvent(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("user_id") String userId,
    @JsonProperty("event_type") BountyHunterEventType eventType,
    @JsonProperty("issue_number") String issueNumber,
    @JsonProperty("pr_number") String prNumber,
    @JsonProperty("points_awarded") long pointsAwarded,
    @JsonProperty("label_name") String labelName,
    @JsonProperty("validated_by_user_id") String validatedByUserId,
    @JsonProperty("timestamp") Instant timestamp) {

  public BountyHunterEvent {
    eventId = sanitizeToken(eventId);
    userId = normalizeUserId(userId);
    eventType = eventType == null ? BountyHunterEventType.ISSUE_LABEL_APPROVED : eventType;
    issueNumber = sanitizeToken(issueNumber);
    prNumber = sanitizeToken(prNumber);
    pointsAwarded = Math.max(0L, pointsAwarded);
    labelName = sanitizeToken(labelName);
    validatedByUserId = normalizeUserId(validatedByUserId);
    timestamp = timestamp == null ? Instant.now() : timestamp;
  }

  private static String normalizeUserId(String raw) {
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
