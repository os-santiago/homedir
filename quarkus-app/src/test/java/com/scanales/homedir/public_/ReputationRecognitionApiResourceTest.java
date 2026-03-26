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
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
@TestProfile(ReputationRecognitionApiResourceTest.Profile.class)
class ReputationRecognitionApiResourceTest {

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "metrics.buffer.max-size", "200",
          "reputation.engine.enabled", "true",
          "reputation.recognition.enabled", "true",
          "reputation.recognition.daily-limit", "2",
          "reputation.recognition.cooldown-seconds", "300");
    }
  }

  @Inject ReputationEngineService reputationEngineService;
  @Inject UserProfileService userProfileService;

  @BeforeEach
  void setUp() {
    reputationEngineService.resetForTests();
    userProfileService.linkGithub(
        "target.one@example.com",
        "Target One",
        "target.one@example.com",
        new UserProfile.GithubAccount(
            "target-one",
            "https://github.com/target-one",
            "https://avatars.githubusercontent.com/u/8010",
            "8010",
            Instant.parse("2026-03-01T00:00:00Z")));
    userProfileService.linkGithub(
        "target.two@example.com",
        "Target Two",
        "target.two@example.com",
        new UserProfile.GithubAccount(
            "target-two",
            "https://github.com/target-two",
            "https://avatars.githubusercontent.com/u/8011",
            "8011",
            Instant.parse("2026-03-01T00:00:00Z")));
  }

  @Test
  void recognitionRequiresAuthentication() {
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "target_user_id",
                "target.one@example.com",
                "source_object_type",
                "community_content",
                "source_object_id",
                "thread-1",
                "recognition_type",
                "recommended"))
        .when()
        .post("/api/community/reputation/recognitions")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "validator@example.com")
  void recognitionRejectsSelfRecognition() {
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "target_user_id",
                "validator@example.com",
                "source_object_type",
                "community_content",
                "source_object_id",
                "thread-self",
                "recognition_type",
                "recommended"))
        .when()
        .post("/api/community/reputation/recognitions")
        .then()
        .statusCode(400)
        .body(containsString("recognition_self_not_allowed"));
  }

  @Test
  @TestSecurity(user = "validator@example.com")
  void recognitionAcceptsRecommendedAndTracksValidatedSignal() {
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "target_user_id",
                "target.one@example.com",
                "source_object_type",
                "community_content",
                "source_object_id",
                "thread-rec-1",
                "recognition_type",
                "recommended"))
        .when()
        .post("/api/community/reputation/recognitions")
        .then()
        .statusCode(200)
        .body(containsString("\"recognition_type\":\"recommended\""))
        .body(containsString("\"event_type\":\"content_recommended\""));

    ReputationEngineService.EngineSnapshot snapshot = reputationEngineService.snapshot();
    boolean found =
        snapshot.eventsById().values().stream()
            .anyMatch(
                event ->
                    "target.one@example.com".equals(event.actorUserId())
                        && "content_recommended".equals(event.eventType())
                        && "validator@example.com".equals(event.validatedByUserId())
                        && "recommended".equals(event.validationType()));
    assertTrue(found);
  }

  @Test
  @TestSecurity(user = "validator@example.com")
  void recognitionAppliesCooldownForDuplicateSignal() {
    Map<String, String> payload =
        Map.of(
            "target_user_id",
            "target.one@example.com",
            "source_object_type",
            "community_content",
            "source_object_id",
            "thread-cooldown",
            "recognition_type",
            "helpful");

    given()
        .contentType("application/json")
        .body(payload)
        .when()
        .post("/api/community/reputation/recognitions")
        .then()
        .statusCode(200);

    given()
        .contentType("application/json")
        .body(payload)
        .when()
        .post("/api/community/reputation/recognitions")
        .then()
        .statusCode(429)
        .body(containsString("recognition_cooldown_active"));
  }

  @Test
  @TestSecurity(user = "validator@example.com")
  void recognitionCooldownIsScopedBySourceObjectType() {
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "target_user_id",
                "target.one@example.com",
                "source_object_type",
                "community_content",
                "source_object_id",
                "shared-id",
                "recognition_type",
                "helpful"))
        .when()
        .post("/api/community/reputation/recognitions")
        .then()
        .statusCode(200);

    given()
        .contentType("application/json")
        .body(
            Map.of(
                "target_user_id",
                "target.one@example.com",
                "source_object_type",
                "cfp_submission",
                "source_object_id",
                "shared-id",
                "recognition_type",
                "helpful"))
        .when()
        .post("/api/community/reputation/recognitions")
        .then()
        .statusCode(200);
  }

  @Test
  @TestSecurity(user = "validator@example.com")
  void recognitionEnforcesDailyLimit() {
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "target_user_id",
                "target.one@example.com",
                "source_object_type",
                "community_content",
                "source_object_id",
                "thread-limit-1",
                "recognition_type",
                "recommended"))
        .when()
        .post("/api/community/reputation/recognitions")
        .then()
        .statusCode(200);

    given()
        .contentType("application/json")
        .body(
            Map.of(
                "target_user_id",
                "target.two@example.com",
                "source_object_type",
                "community_content",
                "source_object_id",
                "thread-limit-2",
                "recognition_type",
                "standout"))
        .when()
        .post("/api/community/reputation/recognitions")
        .then()
        .statusCode(200);

    given()
        .contentType("application/json")
        .body(
            Map.of(
                "target_user_id",
                "target.one@example.com",
                "source_object_type",
                "community_content",
                "source_object_id",
                "thread-limit-3",
                "recognition_type",
                "helpful"))
        .when()
        .post("/api/community/reputation/recognitions")
        .then()
        .statusCode(429)
        .body(containsString("recognition_daily_limit_reached"));
  }
}
