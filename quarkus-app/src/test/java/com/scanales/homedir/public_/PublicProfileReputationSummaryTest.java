package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.TestDataDir;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.reputation.ReputationEngineService;
import com.scanales.homedir.service.UserProfileService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
@TestProfile(PublicProfileReputationSummaryTest.Profile.class)
class PublicProfileReputationSummaryTest {

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "metrics.buffer.max-size", "200",
          "reputation.engine.enabled", "true",
          "reputation.profile.summary.enabled", "true");
    }
  }

  private static final String USER_ID = "reputation.user@example.com";

  @Inject UserProfileService userProfileService;
  @Inject ReputationEngineService reputationEngineService;

  @BeforeEach
  void setUp() {
    reputationEngineService.resetForTests();
    userProfileService.linkGithub(
        USER_ID,
        "Reputation User",
        USER_ID,
        new UserProfile.GithubAccount(
            "reputation-user",
            "https://github.com/reputation-user",
            "https://avatars.githubusercontent.com/u/9101",
            "9101",
            Instant.parse("2026-03-01T00:00:00Z")));
    assertTrue(reputationEngineService.trackQuestCompleted(USER_ID, "challenge-r1"));
    assertTrue(reputationEngineService.trackContentPublished(USER_ID, "thread-r1"));
    assertTrue(reputationEngineService.trackEventAttended(USER_ID, "talk-r1"));
    assertTrue(reputationEngineService.trackEventSpeaker(USER_ID, "submission-r1", "event-r1"));
  }

  @Test
  void publicProfileShowsReputationSummaryInEnglish() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/u/reputation-user")
        .then()
        .statusCode(200)
        .body(containsString("Reputation snapshot"))
        .body(containsString("This month: Top 1"))
        .body(containsString("Known for"))
        .body(containsString("Contribution"))
        .body(containsString("Rising Member"))
        .body(containsString("Weekly growth +42"));
  }

  @Test
  void publicProfileShowsReputationSummaryInSpanish() {
    given()
        .header("Accept-Language", "es")
        .when()
        .get("/u/reputation-user")
        .then()
        .statusCode(200)
        .body(containsString("Resumen de reputación"))
        .body(containsString("Este mes: Top 1"))
        .body(containsString("Conocido por"))
        .body(containsString("Contribución"))
        .body(containsString("Miembro en ascenso"))
        .body(containsString("Crecimiento semanal +42"));
  }
}
