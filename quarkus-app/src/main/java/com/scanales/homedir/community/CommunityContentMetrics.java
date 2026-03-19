package com.scanales.homedir.community;

import java.time.Instant;

public record CommunityContentMetrics(
    int cacheSize,
    Instant lastLoadTime,
    long loadDurationMs,
    int filesLoaded,
    int filesInvalid) {
}

