package com.scanales.eventflow.challenges;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ChallengeStateSnapshot(
    @JsonProperty("schema_version") int schemaVersion,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("progress_by_user") Map<String, List<ChallengeProgress>> progressByUser) {

  public static final int SCHEMA_VERSION = 1;

  public ChallengeStateSnapshot {
    progressByUser = sanitize(progressByUser);
  }

  public static ChallengeStateSnapshot empty() {
    return new ChallengeStateSnapshot(SCHEMA_VERSION, Instant.now(), Map.of());
  }

  private static Map<String, List<ChallengeProgress>> sanitize(Map<String, List<ChallengeProgress>> raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    Map<String, List<ChallengeProgress>> sanitized = new LinkedHashMap<>();
    for (Map.Entry<String, List<ChallengeProgress>> entry : raw.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        continue;
      }
      sanitized.put(
          entry.getKey().trim().toLowerCase(java.util.Locale.ROOT),
          entry.getValue() == null ? List.of() : List.copyOf(entry.getValue()));
    }
    return Map.copyOf(sanitized);
  }
}
