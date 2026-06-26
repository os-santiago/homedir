package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.scanales.homedir.TestDataDir;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.reputation.ReputationEngineService;
import com.scanales.homedir.service.GithubService;
import com.scanales.homedir.service.GithubService.GithubCoder;
import com.scanales.homedir.service.UserProfileService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
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
  @InjectMock GithubService githubService;

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

    assertTrue(
        reputationEngineService.trackContentPublished("hub.user.one@example.com", "thread-a"));
    assertTrue(
        reputationEngineService.trackEventSpeaker(
            "hub.user.one@example.com", "submission-a", "event-a"));
    assertTrue(reputationEngineService.trackQuestCompleted("hub.user.one@example.com", "quest-a"));

    assertTrue(reputationEngineService.trackQuestCompleted("hub.user.two@example.com", "quest-b"));
    assertTrue(reputationEngineService.trackEventAttended("hub.user.two@example.com", "talk-b"));
    assertTrue(
        reputationEngineService.trackVolunteerEngaged(
            "hub.user.two@example.com", "volunteer", "volunteer-b"));
    assertTrue(
        reputationEngineService.trackRecognition(
            "content_recommended",
            "hub.user.two@example.com",
            "community_content",
            "thread-rec-1",
            "validator@example.com",
            "recommended"));

    when(githubService.fetchHomeProjectCoders())
        .thenReturn(
            List.of(
                new GithubCoder(
                    "hub-user-two",
                    "https://avatars.githubusercontent.com/u/7002",
                    "https://github.com/hub-user-two",
                    9,
                    4,
                    3,
                    16),
                new GithubCoder(
                    "hub-user-one",
                    "https://avatars.githubusercontent.com/u/7001",
                    "https://github.com/hub-user-one",
                    5,
                    1,
                    1,
                    7)));
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
        .body(containsString("Current reputation leaders"))
        .body(containsString("This month"))
        .body(containsString("This week"))
        .body(containsString("Weekly leaderboard"))
        .body(containsString("Monthly leaderboard"))
        .body(containsString("Rising members"))
        .body(containsString("Builders"))
        .body(containsString("Helpers"))
        .body(containsString("Learners"))
        .body(containsString("Coders"))
        .body(containsString("Speakers"))
        .body(
            containsString(
                "Members with the highest combined sum of commits, issues, and pull requests."))
        .body(containsString("Recognized contributions"))
        .body(containsString("How to grow from here"))
        .body(containsString("What to focus on"))
        .body(containsString("Next best move"))
        .body(containsString("How reputation works"))
        .body(containsString("href=\"/comunidad/reputation-hub/how\""))
        .body(containsString("href=\"/reputation-hub\""))
        .body(containsString("/css/reputation-hub.css?v="))
        .body(containsString("/js/reputation-hub-vitals.js?v="))
        .body(containsString("data-reputation-vitals=\"hub\""))
        .body(not(containsString("/comunidad/lta")))
        .body(not(containsString("href=\"/comunidad/picks\"")))
        .body(not(containsString("href=\"/comunidad/propose\"")))
        .body(not(containsString("Community Board now lives in Reputation Hub")))
        .body(containsString("hub-user-one"))
        .body(containsString("hub-user-two"))
        .body(containsString("Community recommended"))
        .body(not(containsString("data-recognition-target=\"hub.user.two@example.com\"")));
  }

  @Test
  void reputationHubRendersLocalizedCopyInSpanish() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/comunidad/reputation-hub")
        .then()
        .statusCode(200)
        .body(containsString("Reputation Hub"))
        .body(containsString("Current reputation leaders"))
        .body(containsString("This month"))
        .body(containsString("This week"))
        .body(containsString("Weekly leaderboard"))
        .body(containsString("Monthly leaderboard"))
        .body(containsString("Rising members"))
        .body(containsString("Builders"))
        .body(containsString("Helpers"))
        .body(containsString("Learners"))
        .body(containsString("Coders"))
        .body(containsString("Speakers"))
        .body(containsString("Recognized contributions"))
        .body(containsString("How to grow from here"))
        .body(containsString("What to focus on"))
        .body(containsString("Next best move"))
        .body(containsString("How reputation works"))
        .body(containsString("href=\"/reputation-hub\""))
        .body(containsString("/css/reputation-hub.css?v="))
        .body(containsString("/js/reputation-hub-vitals.js?v="))
        .body(not(containsString("href=\"/comunidad/picks\"")))
        .body(not(containsString("href=\"/comunidad/propose\"")))
        .body(containsString("hub-user-one"))
        .body(containsString("hub-user-two"))
        .body(containsString("Community recommended"));
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
        .body(containsString("href=\"/reputation-hub\""))
        .body(not(containsString("href=\"/comunidad/picks\"")))
        .body(not(containsString("href=\"/comunidad/propose\"")))
        .body(containsString("href=\"/comunidad/reputation-hub\""));
  }

  @Test
  void reputationHubHowPageRendersInSpanish() {
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
        .body(containsString("href=\"/reputation-hub\""))
        .body(not(containsString("href=\"/comunidad/picks\"")))
        .body(not(containsString("href=\"/comunidad/propose\"")))
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
