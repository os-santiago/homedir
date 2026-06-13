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
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
@TestProfile(ReputationHubEmptyCategoryVisibilityTest.Profile.class)
class ReputationHubEmptyCategoryVisibilityTest {

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
        "hub.empty.one@example.com",
        "Hub Empty One",
        "hub.empty.one@example.com",
        new UserProfile.GithubAccount(
            "hub-empty-one",
            "https://github.com/hub-empty-one",
            "https://avatars.githubusercontent.com/u/7101",
            "7101",
            Instant.parse("2026-03-01T00:00:00Z")));

    assertTrue(reputationEngineService.trackContentPublished("hub.empty.one@example.com", "thread-a"));
    assertTrue(
        reputationEngineService.trackEventSpeaker(
            "hub.empty.one@example.com", "submission-a", "event-a"));
    assertTrue(reputationEngineService.trackQuestCompleted("hub.empty.one@example.com", "quest-a"));
  }

  @Test
  void emptyHelperCategoryIsHidden() {
    given()
        .header("Accept-Language", "en")
        .when()
        .get("/comunidad/reputation-hub")
        .then()
        .statusCode(200)
        .body(containsString("Builders"))
        .body(containsString("Speakers"))
        .body(not(containsString("Helpers")))
        .body(not(containsString("No reputation data yet for this view.")));
  }
}
