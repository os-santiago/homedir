package com.scanales.homedir.private_;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.TestDataDir;
import com.scanales.homedir.reputation.ReputationEngineService;
import com.scanales.homedir.service.PersistenceService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
@TestProfile(AdminReputationPhase2ApiResourceTest.Profile.class)
class AdminReputationPhase2ApiResourceTest {

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "metrics.buffer.max-size", "200",
          "reputation.engine.enabled", "true",
          "reputation.shadow.read.enabled", "true");
    }
  }

  @Inject ReputationEngineService reputationEngineService;
  @Inject PersistenceService persistenceService;

  @BeforeEach
  void setUp() {
    reputationEngineService.resetForTests();
    persistenceService.flush();
  }

  @Test
  @TestSecurity(user = "alice")
  void nonAdminCannotAccessPhase2() {
    given().when().get("/api/private/admin/reputation/phase2/diagnostics").then().statusCode(403);
    given().when().get("/api/private/admin/reputation/phase2/user/alice@example.com").then().statusCode(403);
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanAccessPhase2DiagnosticsAndUserExplainability() {
    reputationEngineService.trackQuestCompleted("alice@example.com", "challenge-1");
    reputationEngineService.trackEventAttended("alice@example.com", "talk-1");
    reputationEngineService.trackContentPublished("alice@example.com", "thread-1");
    reputationEngineService.trackEventSpeaker("bob@example.com", "submission-1", "event-2026");
    persistenceService.flush();

    Map<String, Object> diagnostics =
        given()
            .when()
            .get("/api/private/admin/reputation/phase2/diagnostics")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("$");
    assertTrue(((Number) diagnostics.get("totalEvents")).longValue() >= 4L);
    assertTrue(((Number) diagnostics.get("totalUsers")).longValue() >= 2L);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> topTypes = (List<Map<String, Object>>) diagnostics.get("topEventTypes");
    assertTrue(topTypes.stream().anyMatch(row -> "quest_completed".equals(row.get("eventType"))));

    Map<String, Object> user =
        given()
            .when()
            .get("/api/private/admin/reputation/phase2/user/alice@example.com?limit=3")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("$");
    assertTrue("alice@example.com".equals(user.get("userId")));
    Map<String, Object> aggregate = (Map<String, Object>) user.get("aggregate");
    assertTrue(((Number) aggregate.get("total_score")).longValue() >= 28L);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> recentEvents = (List<Map<String, Object>>) user.get("recentEvents");
    assertTrue(recentEvents.size() <= 3);
    assertTrue(recentEvents.stream().allMatch(row -> row.get("impactBand") != null));
  }
}
