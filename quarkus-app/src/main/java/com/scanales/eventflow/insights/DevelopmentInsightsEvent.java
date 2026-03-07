package com.scanales.eventflow.insights;

import java.time.Instant;
import java.util.Map;

/** Immutable append-only event for the development insights ledger. */
public record DevelopmentInsightsEvent(
    String eventId,
    String initiativeId,
    String type,
    Instant at,
    Map<String, String> metadata) {
}

