package com.scanales.eventflow.challenges;

import java.util.LinkedHashMap;
import java.util.Map;

public record ChallengeDefinition(
    String id,
    String title,
    String description,
    int rewardHcoin,
    Map<String, Integer> activityTargets) {

  public ChallengeDefinition {
    id = id == null ? null : id.trim().toLowerCase(java.util.Locale.ROOT);
    title = title == null ? "" : title.trim();
    description = description == null ? "" : description.trim();
    rewardHcoin = Math.max(0, rewardHcoin);
    activityTargets = sanitizeTargets(activityTargets);
  }

  public int totalSteps() {
    return activityTargets.values().stream().mapToInt(Integer::intValue).sum();
  }

  private static Map<String, Integer> sanitizeTargets(Map<String, Integer> raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    Map<String, Integer> sanitized = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> entry : raw.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
        continue;
      }
      int target = Math.max(0, entry.getValue());
      if (target > 0) {
        sanitized.put(entry.getKey().trim().toLowerCase(java.util.Locale.ROOT), target);
      }
    }
    return Map.copyOf(sanitized);
  }
}
