package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.model.QuestClass;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class GamificationServiceTest {

  @Inject GamificationService gamificationService;
  @Inject UserProfileService userProfileService;

  @Test
  void dailyCheckinAwardsXpOncePerDay() {
    String userId = "daily.checkin@example.com";

    assertTrue(gamificationService.award(userId, GamificationActivity.DAILY_CHECKIN));
    assertFalse(gamificationService.award(userId, GamificationActivity.DAILY_CHECKIN));

    var profile = userProfileService.find(userId).orElseThrow();
    assertEquals(10, profile.getCurrentXp());
    assertEquals(10, profile.getClassXp(QuestClass.MAGE));
  }

  @Test
  void oneTimeActivityAwardsOnlyOnce() {
    String userId = "link.github@example.com";

    assertTrue(gamificationService.award(userId, GamificationActivity.GITHUB_LINKED));
    assertFalse(gamificationService.award(userId, GamificationActivity.GITHUB_LINKED));

    var profile = userProfileService.find(userId).orElseThrow();
    assertEquals(25, profile.getCurrentXp());
    assertEquals(25, profile.getClassXp(QuestClass.ENGINEER));
  }

  @Test
  void warriorEventsExplorationAwardsOnlyOncePerDay() {
    String userId = "events.warrior@example.com";

    assertTrue(
        gamificationService.award(
            userId, GamificationActivity.WARRIOR_EVENTS_EXPLORATION, "events"));
    assertFalse(
        gamificationService.award(
            userId, GamificationActivity.WARRIOR_EVENTS_EXPLORATION, "events"));

    var profile = userProfileService.find(userId).orElseThrow();
    assertEquals(4, profile.getCurrentXp());
    assertEquals(4, profile.getClassXp(QuestClass.WARRIOR));
  }
}
