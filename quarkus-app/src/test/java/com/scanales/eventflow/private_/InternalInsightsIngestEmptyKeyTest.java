package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(InternalInsightsIngestEmptyKeyTest.EmptyIngestKeyProfile.class)
public class InternalInsightsIngestEmptyKeyTest {

  @Test
  public void emptyConfiguredKeyDoesNotCrashAndRejectsRequests() {
    Map<String, Object> request =
        Map.of(
            "initiativeId",
            "internal-" + UUID.randomUUID(),
            "title",
            "Internal ingest empty key test",
            "definitionStartedAt",
            "2026-03-07T00:00:00Z",
            "metadata",
            Map.of("source", "test"));
    given()
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Insights-Key", "any-key")
        .body(request)
        .when()
        .post("/api/internal/insights/initiatives/start")
        .then()
        .statusCode(401);
  }

  public static class EmptyIngestKeyProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "insights.ingest.enabled", "true",
          "insights.ingest.key", "");
    }
  }
}
