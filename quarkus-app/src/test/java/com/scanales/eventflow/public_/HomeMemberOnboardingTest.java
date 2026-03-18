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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(
    user = "member@example.com",
    roles = {"user"})
class HomeMemberOnboardingTest {

  @Inject UserProfileService userProfileService;
  @Inject EconomyService economyService;
  @Inject NotificationService notificationService;
  @Inject ChallengeService challengeService;

  @BeforeEach
  void setup() {
    challengeService.resetForTests();
    userProfileService.upsert("member@example.com", "Member Example", "member@example.com");
    userProfileService.addXp("member@example.com", 140, "home-test", QuestClass.MAGE);
    userProfileService.addXp("member@example.com", 80, "home-test", QuestClass.ENGINEER);
    userProfileService.linkGithub(
        "member@example.com",
        "Member Example",
        "member@example.com",
        new UserProfile.GithubAccount(
            "member-example",
            "https://github.com/member-example",
            "https://avatars.githubusercontent.com/u/100",
            "100",
            java.time.Instant.now()));
    challengeService.recordActivity("member@example.com", GamificationActivity.COMMUNITY_VOTE);
    challengeService.recordActivity("member@example.com", GamificationActivity.COMMUNITY_BOARD_MEMBERS_VIEW);
    economyService.rewardFromGamification("member@example.com", "home-test", 600, "home-test");
    notificationService.reset();
    NotificationConfig.enabled = true;
    NotificationConfig.maxQueueSize = 10_000;
    NotificationConfig.dropOnQueueFull = false;
    NotificationConfig.userCap = 100;
    NotificationConfig.globalCap = 1_000;
    NotificationConfig.dedupeWindow = Duration.ofMinutes(30);
    notificationService.enqueue(notification("CFP shortlist updated", "Your proposal moved to the next review step."));
    notificationService.enqueue(notification("Volunteer lounge opened", "Accepted volunteers can already coordinate inside the event space."));
  }

  @Test
  void homeShowsStarterMissionAndPersonalizedActionsForAuthenticatedMember() {
    userProfileService.updateLocale("member@example.com", "en");
    given()
        .header("Accept-Language", "en")
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("Get your first visible win"))
        .body(containsString("Link GitHub"))
        .body(containsString("Link Discord"))
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
        .body(containsString("CFP shortlist updated"))
        .body(containsString("Check your notifications"))
        .body(containsString("Mage is leading"))
        .body(containsString("Profile Glow"));
  }

  @Test
  void homeShowsGamificationPanelsInSpanishForAuthenticatedMember() {
    userProfileService.updateLocale("member@example.com", "es");
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
        .body(containsString("2 sin leer"));
  }

  private Notification notification(String title, String message) {
    Notification notification = new Notification();
    notification.userId = "member@example.com";
    notification.type = NotificationType.SOCIAL;
    notification.talkId = title.toLowerCase().replace(' ', '-');
    notification.title = title;
    notification.message = message;
    return notification;
  }
}
