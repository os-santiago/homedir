package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.eventflow.challenges.ChallengeService;
import com.scanales.eventflow.economy.EconomyService;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.model.QuestClass;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.notifications.Notification;
import com.scanales.eventflow.notifications.NotificationConfig;
import com.scanales.eventflow.notifications.NotificationService;
import com.scanales.eventflow.notifications.NotificationType;
import com.scanales.eventflow.service.UserProfileService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HomeMemberOnboardingTest {

  @Inject UserProfileService userProfileService;
  @Inject EconomyService economyService;
  @Inject NotificationService notificationService;
  @Inject ChallengeService challengeService;

  @BeforeEach
  void setup() {
    challengeService.resetForTests();
    seedHomeMember("member-en@example.com");
    seedHomeMember("member-es@example.com");
    userProfileService.upsert("trend-peer@example.com", "Trend Peer", "trend-peer@example.com");
    challengeService.recordActivity("trend-peer@example.com", GamificationActivity.EVENT_VIEW);
    challengeService.recordActivity("trend-peer@example.com", GamificationActivity.AGENDA_VIEW);
    challengeService.recordActivity("trend-peer@example.com", GamificationActivity.TALK_VIEW);
    notificationService.reset();
    NotificationConfig.enabled = true;
    NotificationConfig.maxQueueSize = 10_000;
    NotificationConfig.dropOnQueueFull = false;
    NotificationConfig.userCap = 100;
    NotificationConfig.globalCap = 1_000;
    NotificationConfig.dedupeWindow = Duration.ofMinutes(30);
    notificationService.enqueue(
        notification(
            "member-en@example.com",
            "CFP shortlist updated",
            "Your proposal moved to the next review step."));
    notificationService.enqueue(
        notification(
            "member-en@example.com",
            "Volunteer lounge opened",
            "Accepted volunteers can already coordinate inside the event space."));
    notificationService.enqueue(
        notification(
            "member-es@example.com",
            "CFP shortlist updated",
            "Your proposal moved to the next review step."));
    notificationService.enqueue(
        notification(
            "member-es@example.com",
            "Volunteer lounge opened",
            "Accepted volunteers can already coordinate inside the event space."));
  }

  @Test
  @TestSecurity(
      user = "member-en@example.com",
      roles = {"user"})
  void homeShowsStarterMissionAndPersonalizedActionsForAuthenticatedMember() {
    userProfileService.updateLocale("member-en@example.com", "en");
    given()
        .header("Accept-Language", "en")
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("Get your first visible win"))
        .body(containsString("GitHub linked"))
        .body(containsString("Discord linked"))
        .body(containsString("Vote one Community Pick"))
        .body(containsString("Your next best moves"))
        .body(containsString("Profile setup"))
        .body(containsString("Open rewards"))
        .body(containsString("Current challenges"))
        .body(containsString("Community Scout"))
        .body(containsString("Event Explorer"))
        .body(containsString("Open Source Identity"))
        .body(containsString("Class momentum"))
        .body(containsString("Reward runway"))
        .body(containsString("Your community today"))
        .body(containsString("What needs your attention"))
        .body(containsString("A short return routine"))
        .body(containsString("What the community is completing"))
        .body(containsString("Community Scout"))
        .body(containsString("Your verified pace"))
        .body(containsString("Rank #1"))
        .body(containsString("Open Source Identity"))
        .body(containsString("CFP shortlist updated"))
        .body(containsString("Check your notifications"))
        .body(containsString("Mage is leading"))
        .body(containsString("Profile Glow"));
  }

  @Test
  @TestSecurity(
      user = "member-es@example.com",
      roles = {"user"})
  void homeShowsGamificationPanelsInSpanishForAuthenticatedMember() {
    userProfileService.updateLocale("member-es@example.com", "es");
    given()
        .header("Accept-Language", "es")
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("Challenges actuales"))
        .body(containsString("Completados"))
        .body(containsString("Open Source Identity"))
        .body(containsString("Impulso por clase"))
        .body(containsString("Ruta de recompensas"))
        .body(containsString("Clase más activa"))
        .body(containsString("Saldo HCoin"))
        .body(containsString("Tu comunidad hoy"))
        .body(containsString("Lo que necesita tu atención"))
        .body(containsString("Una rutina corta de regreso"))
        .body(containsString("Lo que la comunidad está completando"))
        .body(containsString("Tu ritmo verificado"))
        .body(containsString("Rango #"))
        .body(containsString("2 sin leer"));
  }

  private void seedHomeMember(String userId) {
    userProfileService.upsert(userId, "Member Example", userId);
    userProfileService.addXp(userId, 140, "home-test", QuestClass.MAGE);
    userProfileService.addXp(userId, 80, "home-test", QuestClass.ENGINEER);
    userProfileService.linkGithub(
        userId,
        "Member Example",
        userId,
        new UserProfile.GithubAccount(
            "member-example",
            "https://github.com/member-example",
            "https://avatars.githubusercontent.com/u/100",
            "100",
            Instant.now()));
    userProfileService.linkDiscord(
        userId,
        "Member Example",
        userId,
        new UserProfile.DiscordAccount(
            "member-example",
            "member-example",
            "https://discord.com/users/member-example",
            "https://cdn.discordapp.com/embed/avatars/1.png",
            Instant.now()));
    challengeService.recordActivity(userId, GamificationActivity.COMMUNITY_VOTE);
    challengeService.recordActivity(userId, GamificationActivity.COMMUNITY_VOTE);
    challengeService.recordActivity(userId, GamificationActivity.COMMUNITY_VOTE);
    challengeService.recordActivity(userId, GamificationActivity.COMMUNITY_BOARD_MEMBERS_VIEW);
    challengeService.recordActivity(userId, GamificationActivity.BOARD_PROFILE_OPEN);
    economyService.rewardFromGamification(userId, "home-test", 600, "home-test");
  }

  private Notification notification(String userId, String title, String message) {
    Notification notification = new Notification();
    notification.userId = userId;
    notification.type = NotificationType.SOCIAL;
    notification.talkId = title.toLowerCase().replace(' ', '-');
    notification.title = title;
    notification.message = message;
    return notification;
  }
}
