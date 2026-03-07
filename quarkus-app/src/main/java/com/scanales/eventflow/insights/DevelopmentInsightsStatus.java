package com.scanales.eventflow.insights;

import java.time.Instant;
import java.util.Map;

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
    int prValidationPassedEvents,
    int prValidationFailedEvents,
    int productionReleaseFailedEvents,
    int prValidationTotalEvents,
    Long prValidationSuccessRatePct,
    int productionOutcomeEvents,
    Long productionSuccessRatePct,
    int productionVerifiedEventsLast7Days,
    int productionReleaseFailedEventsLast7Days,
    Long productionSuccessRatePctLast7Days,
    Long avgLeadHoursToMerge,
    Long avgLeadHoursToProduction,
    int eventsLast7Days,
    int eventsPrevious7Days,
    Long eventsTrendPct,
    int activeInitiativesLast7Days,
    Map<String, Integer> topEventTypesLast7Days,
    Long minutesSinceLastEvent,
    boolean stale,
    Instant lastEventAt,
    long compactions,
    long loadErrors,
    long writeErrors) {
}
