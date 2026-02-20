package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class QuestServiceProgressionTest {

  @Inject QuestService questService;

  @Test
  void level1000TargetIsProjectedNear100kXp() {
    int xpLevel1000 = questService.getXpForLevel(1000);
    assertTrue(Math.abs(xpLevel1000 - 100_000) <= 2, "level 1000 xp should stay near 100k");
    assertEquals(1000, questService.calculateLevel(xpLevel1000));
  }

  @Test
  void progressionSupportsTechnicalCap() {
    int xpLevel9999 = questService.getXpForLevel(9999);
    int xpLevel1000 = questService.getXpForLevel(1000);
    assertTrue(xpLevel9999 > xpLevel1000, "max level threshold should be above level 1000");
    assertEquals(9999, questService.calculateLevel(xpLevel9999));
  }

  @Test
  void earlyLevelsStayStable() {
    assertEquals(0, questService.getXpForLevel(1));
    assertEquals(100, questService.getXpForLevel(2));
    assertEquals(2, questService.calculateLevel(150));
  }
}
