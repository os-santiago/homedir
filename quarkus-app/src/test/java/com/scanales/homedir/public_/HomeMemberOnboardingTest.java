package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.homedir.challenges.ChallengeService;
import com.scanales.homedir.economy.EconomyService;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.model.QuestClass;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.notifications.Notification;
import com.scanales.homedir.notifications.NotificationConfig;
import com.scanales.homedir.notifications.NotificationService;
import com.scanales.homedir.notifications.NotificationType;
import com.scanales.homedir.service.UserProfileService;
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
        .body(containsString("HomeDir focuses on events, community news, and collaboration."))
        .body(containsString("DevOpsDays Santiago is the first HomeDir priority."))
        .body(containsString("Community and local event news"))
        .body(containsString("Choose how to collaborate"))
        .body(containsString("DevOpsDays Santiago Call for Papers"))
        .body(containsString("DevOpsDays Santiago Call for Volunteers"))
        .body(containsString("DevOpsDays Santiago Call for Sponsors"));
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
        .body(containsString("HomeDir se enfoca en eventos, noticias de comunidad y colaboración."))
        .body(containsString("DevOpsDays Santiago es la primera prioridad de HomeDir."))
        .body(containsString("Noticias de comunidad y eventos locales"))
        .body(containsString("Elige cómo colaborar"))
        .body(containsString("DevOpsDays Santiago Call for Papers"))
        .body(containsString("DevOpsDays Santiago Call for Volunteers"))
        .body(containsString("DevOpsDays Santiago Call for Sponsors"));
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
