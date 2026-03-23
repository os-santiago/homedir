package com.scanales.homedir.reputation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record ReputationStateSnapshot(
    @JsonProperty("schema_version") int schemaVersion,
    @JsonProperty("updated_at") Instant updatedAt,
    Map<String, ReputationEventRecord> events,
    @JsonProperty("aggregates_by_user") Map<String, UserReputationAggregate> aggregatesByUser) {

  public static final int SCHEMA_VERSION = 1;

  public ReputationStateSnapshot {
    schemaVersion = schemaVersion <= 0 ? SCHEMA_VERSION : schemaVersion;
    updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    events = sanitizeEvents(events);
    aggregatesByUser = sanitizeAggregates(aggregatesByUser);
  }

  public static ReputationStateSnapshot empty() {
    return new ReputationStateSnapshot(SCHEMA_VERSION, Instant.now(), Map.of(), Map.of());
  }

  private static Map<String, ReputationEventRecord> sanitizeEvents(Map<String, ReputationEventRecord> raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    Map<String, ReputationEventRecord> sanitized = new LinkedHashMap<>();
    for (Map.Entry<String, ReputationEventRecord> entry : raw.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
        continue;
      }
      sanitized.put(entry.getKey().trim(), entry.getValue());
    }
    return Map.copyOf(sanitized);
  }

  private static Map<String, UserReputationAggregate> sanitizeAggregates(
      Map<String, UserReputationAggregate> raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    Map<String, UserReputationAggregate> sanitized = new LinkedHashMap<>();
    for (Map.Entry<String, UserReputationAggregate> entry : raw.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
        continue;
      }
      sanitized.put(entry.getKey().trim().toLowerCase(java.util.Locale.ROOT), entry.getValue());
    }
    return Map.copyOf(sanitized);
  }
}
