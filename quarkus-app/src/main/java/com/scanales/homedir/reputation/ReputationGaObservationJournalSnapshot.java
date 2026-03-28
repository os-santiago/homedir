package com.scanales.homedir.reputation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ReputationGaObservationJournalSnapshot(
    @JsonProperty("schema_version") int schemaVersion,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("updated_by") String updatedBy,
    @JsonProperty("weekly_cycle_observed") boolean weeklyCycleObserved,
    @JsonProperty("weekly_cycle_observed_at") Instant weeklyCycleObservedAt,
    @JsonProperty("weekly_cycle_observed_by") String weeklyCycleObservedBy,
    @JsonProperty("monthly_cycle_observed") boolean monthlyCycleObserved,
    @JsonProperty("monthly_cycle_observed_at") Instant monthlyCycleObservedAt,
    @JsonProperty("monthly_cycle_observed_by") String monthlyCycleObservedBy,
    @JsonProperty("release_window_one_observed") boolean releaseWindowOneObserved,
    @JsonProperty("release_window_one_observed_at") Instant releaseWindowOneObservedAt,
    @JsonProperty("release_window_one_observed_by") String releaseWindowOneObservedBy,
    @JsonProperty("release_window_two_observed") boolean releaseWindowTwoObserved,
    @JsonProperty("release_window_two_observed_at") Instant releaseWindowTwoObservedAt,
    @JsonProperty("release_window_two_observed_by") String releaseWindowTwoObservedBy) {

  public static final int SCHEMA_VERSION = 1;

  public ReputationGaObservationJournalSnapshot {
    updatedBy = safe(updatedBy);
    weeklyCycleObservedBy = safe(weeklyCycleObservedBy);
    monthlyCycleObservedBy = safe(monthlyCycleObservedBy);
    releaseWindowOneObservedBy = safe(releaseWindowOneObservedBy);
    releaseWindowTwoObservedBy = safe(releaseWindowTwoObservedBy);
  }

  public static ReputationGaObservationJournalSnapshot empty() {
    return new ReputationGaObservationJournalSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        "system",
        false,
        null,
        "",
        false,
        null,
        "",
        false,
        null,
        "",
        false,
        null,
        "");
  }

  public ReputationGaObservationJournalSnapshot withWeeklyCycleObserved(boolean observed, String actor) {
    return new ReputationGaObservationJournalSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        safe(actor),
        observed,
        observed ? Instant.now() : null,
        observed ? safe(actor) : "",
        monthlyCycleObserved,
        monthlyCycleObservedAt,
        monthlyCycleObservedBy,
        releaseWindowOneObserved,
        releaseWindowOneObservedAt,
        releaseWindowOneObservedBy,
        releaseWindowTwoObserved,
        releaseWindowTwoObservedAt,
        releaseWindowTwoObservedBy);
  }

  public ReputationGaObservationJournalSnapshot withMonthlyCycleObserved(boolean observed, String actor) {
    return new ReputationGaObservationJournalSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        safe(actor),
        weeklyCycleObserved,
        weeklyCycleObservedAt,
        weeklyCycleObservedBy,
        observed,
        observed ? Instant.now() : null,
        observed ? safe(actor) : "",
        releaseWindowOneObserved,
        releaseWindowOneObservedAt,
        releaseWindowOneObservedBy,
        releaseWindowTwoObserved,
        releaseWindowTwoObservedAt,
        releaseWindowTwoObservedBy);
  }

  public ReputationGaObservationJournalSnapshot withReleaseWindowOneObserved(
      boolean observed, String actor) {
    return new ReputationGaObservationJournalSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        safe(actor),
        weeklyCycleObserved,
        weeklyCycleObservedAt,
        weeklyCycleObservedBy,
        monthlyCycleObserved,
        monthlyCycleObservedAt,
        monthlyCycleObservedBy,
        observed,
        observed ? Instant.now() : null,
        observed ? safe(actor) : "",
        releaseWindowTwoObserved,
        releaseWindowTwoObservedAt,
        releaseWindowTwoObservedBy);
  }

  public ReputationGaObservationJournalSnapshot withReleaseWindowTwoObserved(
      boolean observed, String actor) {
    return new ReputationGaObservationJournalSnapshot(
        SCHEMA_VERSION,
        Instant.now(),
        safe(actor),
        weeklyCycleObserved,
        weeklyCycleObservedAt,
        weeklyCycleObservedBy,
        monthlyCycleObserved,
        monthlyCycleObservedAt,
        monthlyCycleObservedBy,
        releaseWindowOneObserved,
        releaseWindowOneObservedAt,
        releaseWindowOneObservedBy,
        observed,
        observed ? Instant.now() : null,
        observed ? safe(actor) : "");
  }

  public long completedChecks() {
    long count = 0L;
    if (weeklyCycleObserved) {
      count++;
    }
    if (monthlyCycleObserved) {
      count++;
    }
    if (releaseWindowOneObserved) {
      count++;
    }
    if (releaseWindowTwoObserved) {
      count++;
    }
    return count;
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }
}
