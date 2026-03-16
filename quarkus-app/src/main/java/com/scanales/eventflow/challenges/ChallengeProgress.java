package com.scanales.eventflow.challenges;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record ChallengeProgress(
    @JsonProperty("challenge_id") String challengeId,
    @JsonProperty("activity_counts") Map<String, Integer> activityCounts,
    @JsonProperty("started_at") Instant startedAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("completed_at") Instant completedAt,
    @JsonProperty("reward_granted_at") Instant rewardGrantedAt) {

  public ChallengeProgress {
    challengeId = challengeId == null ? null : challengeId.trim().toLowerCase(Locale.ROOT);
    activityCounts = sanitizeCounts(activityCounts);
  }

  public static ChallengeProgress empty(String challengeId) {
    return new ChallengeProgress(challengeId, Map.of(), null, null, null, null);
  }

  private static Map<String, Integer> sanitizeCounts(Map<String, Integer> raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    Map<String, Integer> sanitized = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> entry : raw.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
        continue;
      }
      sanitized.put(entry.getKey().trim().toLowerCase(Locale.ROOT), Math.max(0, entry.getValue()));
    }
    return Map.copyOf(sanitized);
  }
}
