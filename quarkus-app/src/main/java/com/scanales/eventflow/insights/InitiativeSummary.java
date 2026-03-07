package com.scanales.eventflow.insights;

import java.time.Instant;

/** Aggregated view for one initiative derived from append-only ledger events. */
public record InitiativeSummary(
    String initiativeId,
    String title,
    String state,
    Instant startedAt,
    Instant lastEventAt,
    String lastEventType,
    long totalEvents) {
}

