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

  public boolean isStale(java.time.Duration ttl) {
    if (lastSuccessTime == null) {
      return true;
    }
    return Instant.now().isAfter(lastSuccessTime.plus(ttl));
  }
}
