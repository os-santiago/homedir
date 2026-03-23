package com.scanales.homedir.reputation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record UserReputationAggregate(
    @JsonProperty("user_id") String userId,
    @JsonProperty("total_score") long totalScore,
    @JsonProperty("scores_by_dimension") Map<String, Long> scoresByDimension,
    @JsonProperty("weekly_score") long weeklyScore,
    @JsonProperty("monthly_score") long monthlyScore,
    @JsonProperty("rising_delta") long risingDelta,
    @JsonProperty("updated_at") Instant updatedAt) {

  public UserReputationAggregate {
    userId = normalizeUser(userId);
    totalScore = Math.max(0L, totalScore);
    scoresByDimension = sanitize(scoresByDimension);
    weeklyScore = Math.max(0L, weeklyScore);
    monthlyScore = Math.max(0L, monthlyScore);
    updatedAt = updatedAt == null ? Instant.now() : updatedAt;
  }

  private static String normalizeUser(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  private static Map<String, Long> sanitize(Map<String, Long> raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    Map<String, Long> sanitized = new LinkedHashMap<>();
    for (Map.Entry<String, Long> entry : raw.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        continue;
      }
      long value = entry.getValue() == null ? 0L : Math.max(0L, entry.getValue());
      sanitized.put(entry.getKey().trim().toLowerCase(Locale.ROOT), value);
    }
    return Map.copyOf(sanitized);
  }
}
