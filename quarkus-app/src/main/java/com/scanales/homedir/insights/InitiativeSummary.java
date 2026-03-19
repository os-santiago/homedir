package com.scanales.homedir.insights;

import java.time.Instant;

/** Aggregated view for one initiative derived from append-only ledger events. */
public record InitiativeSummary(
    String initiativeId,
    String title,
    String state,
    Instant startedAt,
    Instant definitionStartedAt,
    Instant prOpenedAt,
    Instant prMergedAt,
    Instant productionVerifiedAt,
    Long leadHoursToMerge,
    Long leadHoursToProduction,
    Instant lastEventAt,
    String lastEventType,
    long totalEvents) {
}
