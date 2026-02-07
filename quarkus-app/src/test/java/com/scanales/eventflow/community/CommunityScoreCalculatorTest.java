package com.scanales.eventflow.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

public class CommunityScoreCalculatorTest {

  @Test
  void computesScoreBaseWithRequestedWeights() {
    CommunityVoteAggregate aggregate = new CommunityVoteAggregate(4, 2, 3, null);
    double score = CommunityScoreCalculator.scoreBase(aggregate);
    assertEquals(8.5d, score, 0.0001d);
  }

  @Test
  void appliesDecayWhenEnabled() {
    CommunityVoteAggregate aggregate = new CommunityVoteAggregate(1, 1, 0, null);
    Instant created = Instant.parse("2026-02-01T00:00:00Z");
    Instant now = Instant.parse("2026-02-07T00:00:00Z");

    double withoutDecay = CommunityScoreCalculator.score(aggregate, created, now, false);
    double withDecay = CommunityScoreCalculator.score(aggregate, created, now, true);

    assertEquals(4d, withoutDecay, 0.0001d);
    assertTrue(withDecay < withoutDecay);
  }
}

