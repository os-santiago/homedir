package com.scanales.homedir.trending;

import java.time.Instant;
import java.util.List;

public record TrendingCacheSnapshot(
    List<TrendingRepo> repos,
    Instant lastRefreshTime,
    Instant lastSuccessTime,
    TrendingPeriod period) {

  public static TrendingCacheSnapshot empty(TrendingPeriod period) {
    return new TrendingCacheSnapshot(List.of(), null, null, period);
  }

  /** Returns true if last successful refresh is older than TTL, or never refreshed. */
  public boolean isStale(java.time.Duration ttl) {
    // ponytail: use lastRefreshTime so failed attempts don't block on-demand retry
    if (lastRefreshTime == null) {
      return true;
    }
    return Instant.now().isAfter(lastRefreshTime.plus(ttl));
  }
}
