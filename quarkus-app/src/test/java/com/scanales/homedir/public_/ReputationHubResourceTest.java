package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.TestDataDir;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.reputation.ReputationEngineService;
import com.scanales.homedir.service.UserProfileService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
@TestProfile(ReputationHubResourceTest.Profile.class)
class ReputationHubResourceTest {

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "metrics.buffer.max-size", "200",
          "reputation.engine.enabled", "true",
          "reputation.hub.ui.enabled", "true");
    }
  }

  @Inject UserProfileService userProfileService;
  @Inject ReputationEngineService reputationEngineService;

  @BeforeEach
  void setUp() {
    reputationEngineService.resetForTests();

    userProfileService.linkGithub(
        "hub.user.one@example.com",
        "Hub User One",
        "hub.user.one@example.com",
        new UserProfile.GithubAccount(
            "hub-user-one",
            "https://github.com/hub-user-one",
            "https://avatars.githubusercontent.com/u/7001",
            "7001",
            Instant.parse("2026-03-01T00:00:00Z")));
    userProfileService.linkGithub(
        "hub.user.two@example.com",
        "Hub User Two",
        "hub.user.two@example.com",
        new UserProfile.GithubAccount(
            "hub-user-two",
            "https://github.com/hub-user-two",
            "https://avatars.githubusercontent.com/u/7002",
            "7002",
            Instant.parse("2026-03-01T00:00:00Z")));

    assertTrue(reputationEngineService.trackContentPublished("hub.user.one@example.com", "thread-a"));
    assertTrue(
        reputationEngineService.trackEventSpeaker(
            "hub.user.one@example.com", "submission-a", "event-a"));
    assertTrue(reputationEngineService.trackQuestCompleted("hub.user.one@example.com", "quest-a"));

    assertTrue(reputationEngineService.trackQuestCompleted("hub.user.two@example.com", "quest-b"));
    assertTrue(reputationEngineService.trackEventAttended("hub.user.two@example.com", "talk-b"));
    assertTrue(
        reputationEngineService.trackRecognition(
            "content_recommended",
            "hub.user.two@example.com",
            "community_content",
            "thread-rec-1",
            "validator@example.com",
            "recommended"));
  }

  @Test
  void reputationHubRendersWeeklyMonthlyAndRisingLeaderboardsInEnglish() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/comunidad/reputation-hub")
        .then()
        .statusCode(200)
        .body(containsString("Reputation Hub"))
        .body(containsString("Weekly leaderboard"))
        .body(containsString("Monthly leaderboard"))
        .body(containsString("Rising members"))
        .body(containsString("Recognized contributions"))
        .body(containsString("How reputation works"))
        .body(containsString("href=\"/comunidad/reputation-hub/how\""))
        .body(containsString("/css/reputation-hub.css?v="))
        .body(containsString("/js/reputation-hub-vitals.js?v="))
        .body(containsString("data-reputation-vitals=\"hub\""))
        .body(containsString("href=\"/comunidad/board\""))
        .body(not(containsString("Community Board now lives in Reputation Hub")))
        .body(containsString("hub-user-one"))
        .body(containsString("hub-user-two"))
        .body(containsString("Community recommended"))
        .body(not(containsString("data-recognition-target=\"hub.user.two@example.com\"")));
  }

  @Test
  void reputationHubRendersLocalizedCopyInSpanish() {
    given()
        .header("Accept-Language", "es")
        .when()
        .get("/comunidad/reputation-hub")
        .then()
        .statusCode(200)
        .body(containsString("Centro de reputación"))
        .body(containsString("Ranking semanal"))
        .body(containsString("Ranking mensual"))
        .body(containsString("Miembros en ascenso"))
        .body(containsString("Contribuciones reconocidas"))
        .body(containsString("Cómo funciona la reputación"))
        .body(containsString("/css/reputation-hub.css?v="))
        .body(containsString("/js/reputation-hub-vitals.js?v="))
        .body(containsString("hub-user-one"))
        .body(containsString("hub-user-two"))
        .body(containsString("Recomendado por la comunidad"));
  }

  @Test
  void reputationHubHowPageRendersInEnglish() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/comunidad/reputation-hub/how")
        .then()
        .statusCode(200)
        .body(containsString("How reputation works"))
        .body(containsString("Top strengths"))
        .body(containsString("Participation"))
        .body(containsString("Contribution"))
        .body(containsString("Recognition"))
        .body(containsString("Consistency"))
        .body(containsString("/css/reputation-hub.css?v="))
        .body(containsString("/js/reputation-hub-vitals.js?v="))
        .body(containsString("data-reputation-vitals=\"how\""))
        .body(containsString("href=\"/comunidad/board\""))
        .body(containsString("href=\"/comunidad/reputation-hub\""));
  }

  @Test
  void reputationHubHowPageRendersInSpanish() {
    given()
        .header("Accept-Language", "es")
        .when()
        .get("/comunidad/reputation-hub/how")
        .then()
        .statusCode(200)
        .body(containsString("Cómo funciona la reputación"))
        .body(containsString("Fortalezas principales"))
        .body(containsString("Participación"))
        .body(containsString("Contribución"))
        .body(containsString("Reconocimiento"))
        .body(containsString("Consistencia"))
        .body(containsString("/css/reputation-hub.css?v="))
        .body(containsString("/js/reputation-hub-vitals.js?v="))
        .body(containsString("href=\"/comunidad/reputation-hub\""));
  }

  @Test
  @TestSecurity(user = "validator@example.com")
  void reputationHubShowsRecognitionActionsWhenUserIsAuthenticated() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/comunidad/reputation-hub")
        .then()
        .statusCode(200)
        .body(containsString("hub-recognition-actions"))
        .body(containsString("data-recognition-target=\"hub.user.two@example.com\""))
        .body(containsString("data-recognition-type=\"recommended\""))
        .body(containsString("/js/reputation-recognition.js?v="));
  }
}
