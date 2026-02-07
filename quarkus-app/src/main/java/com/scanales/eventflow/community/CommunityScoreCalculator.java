package com.scanales.eventflow.community;

import java.time.Duration;
import java.time.Instant;

public final class CommunityScoreCalculator {
  private CommunityScoreCalculator() {}

  public static double scoreBase(CommunityVoteAggregate aggregate) {
    return 3d * aggregate.mustSee() + aggregate.recommended() - 0.5d * aggregate.notForMe();
  }

  public static double score(
      CommunityVoteAggregate aggregate, Instant createdAt, Instant now, boolean applyDecay) {
    double base = scoreBase(aggregate);
    if (!applyDecay || createdAt == null || now == null) {
      return base;
    }
    double days = Math.max(0d, Duration.between(createdAt, now).toHours() / 24d);
    double decay = Math.pow(0.85d, days);
    return base * decay;
  }
}

