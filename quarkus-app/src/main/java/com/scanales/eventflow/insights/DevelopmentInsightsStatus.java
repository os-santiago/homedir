package com.scanales.eventflow.insights;

import java.time.Instant;

/** Runtime status for the development insights ledger. */
public record DevelopmentInsightsStatus(
    boolean enabled,
    String ledgerPath,
    long ledgerBytes,
    int maxEntries,
    int storedEvents,
    int initiatives,
    int startedInitiatives,
    int mergedInitiatives,
    int productionVerifiedInitiatives,
    Instant lastEventAt,
    long compactions,
    long loadErrors,
    long writeErrors) {
}
