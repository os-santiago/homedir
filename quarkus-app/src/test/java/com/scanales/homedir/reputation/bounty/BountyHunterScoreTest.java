package com.scanales.homedir.reputation.bounty;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BountyHunterScoreTest {

  @Test
  void constructor_normalizesUserId() {
    BountyHunterScore score =
        new BountyHunterScore(
            "  TestUser  ", 100L, 50L, 50L, BountyHunterLevel.NOVICE, 2, 1, Instant.now());
    assertEquals("testuser", score.userId());
  }

  @Test
  void constructor_enforcesNonNegativePoints() {
    BountyHunterScore score =
        new BountyHunterScore("user", -10L, -5L, -5L, BountyHunterLevel.NONE, 0, 0, Instant.now());
    assertEquals(0L, score.totalPoints());
    assertEquals(0L, score.issueCreationPoints());
    assertEquals(0L, score.issueResolutionPoints());
  }

  @Test
  void constructor_enforcesNonNegativeCounts() {
    BountyHunterScore score =
        new BountyHunterScore("user", 0L, 0L, 0L, BountyHunterLevel.NONE, -5, -3, Instant.now());
    assertEquals(0, score.issuesCreatedCount());
    assertEquals(0, score.issuesResolvedCount());
  }

  @Test
  void constructor_defaultsLevelToNone() {
    BountyHunterScore score = new BountyHunterScore("user", 0L, 0L, 0L, null, 0, 0, Instant.now());
    assertEquals(BountyHunterLevel.NONE, score.currentLevel());
  }

  @Test
  void constructor_recomputesLevelFromTotalPoints() {
    BountyHunterScore score =
        new BountyHunterScore("user", 240L, 240L, 0L, BountyHunterLevel.NONE, 0, 0, Instant.now());
    assertEquals(BountyHunterLevel.EXPERIENCED, score.currentLevel());
  }

  @Test
  void withAddedIssueCreationPoints_incrementsCorrectly() {
    Instant now = Instant.now();
    BountyHunterScore initial =
        new BountyHunterScore("user", 50L, 50L, 0L, BountyHunterLevel.NOVICE, 2, 0, now);

    BountyHunterScore updated = initial.withAddedIssueCreationPoints(30L, "123");

    assertEquals(80L, updated.totalPoints());
    assertEquals(80L, updated.issueCreationPoints());
    assertEquals(0L, updated.issueResolutionPoints());
    assertEquals(3, updated.issuesCreatedCount());
    assertEquals(0, updated.issuesResolvedCount());
    assertTrue(updated.updatedAt().isAfter(now) || updated.updatedAt().equals(now));
  }

  @Test
  void withAddedIssueResolutionPoints_incrementsCorrectly() {
    Instant now = Instant.now();
    BountyHunterScore initial =
        new BountyHunterScore("user", 40L, 0L, 40L, BountyHunterLevel.NONE, 0, 2, now);

    BountyHunterScore updated = initial.withAddedIssueResolutionPoints(20L, "124");

    assertEquals(60L, updated.totalPoints());
    assertEquals(0L, updated.issueCreationPoints());
    assertEquals(60L, updated.issueResolutionPoints());
    assertEquals(0, updated.issuesCreatedCount());
    assertEquals(3, updated.issuesResolvedCount());
    assertTrue(updated.updatedAt().isAfter(now) || updated.updatedAt().equals(now));
  }

  @Test
  void withAddedPoints_updatesLevelBasedOnTotalPoints() {
    BountyHunterScore initial =
        new BountyHunterScore("user", 40L, 40L, 0L, BountyHunterLevel.NONE, 1, 0, Instant.now());

    BountyHunterScore updated = initial.withAddedIssueCreationPoints(200L, "125");

    assertEquals(240L, updated.totalPoints());
    assertEquals(BountyHunterLevel.EXPERIENCED, updated.currentLevel());
  }
}
