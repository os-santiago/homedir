package com.scanales.homedir.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.challenges.ChallengeService;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.model.QuestClass;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.notifications.NotificationService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class GamificationServiceTest {

  @Inject GamificationService gamificationService;
  @Inject UserProfileService userProfileService;
  @Inject NotificationService notificationService;
  @Inject UsageMetricsService usageMetricsService;
  @Inject ChallengeService challengeService;

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

  @Test
  void notificationsCenterAwardsOnlyOncePerDay() {
    String userId = "notifications.warrior@example.com";

    assertTrue(gamificationService.award(userId, GamificationActivity.NOTIFICATIONS_CENTER_VIEW));
    assertFalse(gamificationService.award(userId, GamificationActivity.NOTIFICATIONS_CENTER_VIEW));

    var profile = userProfileService.find(userId).orElseThrow();
    assertEquals(4, profile.getCurrentXp());
    assertEquals(4, profile.getClassXp(QuestClass.WARRIOR));
  }

  @Test
  void completingChallengeRecordsFunnelAndNotification() {
    String userId = "challenge.complete@example.com";
    notificationService.reset();
    usageMetricsService.reset();
    challengeService.resetForTests();

    userProfileService.upsert(userId, "Challenge Complete", userId);
    userProfileService.updateLocale(userId, "en");
    userProfileService.linkGithub(
        userId,
        "Challenge Complete",
        userId,
        new UserProfile.GithubAccount(
            "challenge-complete",
            "https://github.com/challenge-complete",
            "https://avatars.githubusercontent.com/u/101",
            "101",
            Instant.now()));
    userProfileService.linkDiscord(
        userId,
        "Challenge Complete",
        userId,
        new UserProfile.DiscordAccount(
            "challenge-complete",
            "challenge-complete",
            "https://discord.com/users/challenge-complete",
            "https://cdn.discordapp.com/embed/avatars/0.png",
            Instant.now()));

    assertTrue(gamificationService.award(userId, GamificationActivity.HOME_VIEW));

    var notifications = notificationService.listPage(userId, "all", null, 10);
    assertEquals(1, notifications.items().size());
    assertEquals("Challenge completed", notifications.items().get(0).title);
    assertTrue(notifications.items().get(0).message.contains("Open Source Identity"));

    var counters = usageMetricsService.snapshot();
    assertEquals(1L, counters.getOrDefault("funnel:challenge.started", 0L));
    assertEquals(1L, counters.getOrDefault("funnel:challenge.started.open-source-identity", 0L));
  }
}
