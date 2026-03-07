package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class InternalInsightsIngestResourceTest {

  private static final String INGEST_KEY = "test-ingest-key";

  @Test
  public void rejectsMissingKey() {
    Map<String, Object> request =
        Map.of(
            "initiativeId",
            "internal-" + UUID.randomUUID(),
            "title",
            "Internal ingest test",
            "definitionStartedAt",
            "2026-03-07T00:00:00Z",
            "metadata",
            Map.of("source", "test"));
    given()
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .when()
        .post("/api/internal/insights/initiatives/start")
        .then()
        .statusCode(401);
  }

  @Test
  public void rejectsInvalidKey() {
    Map<String, Object> request =
        Map.of(
            "initiativeId",
            "internal-" + UUID.randomUUID(),
            "title",
            "Internal ingest test",
            "definitionStartedAt",
            "2026-03-07T00:00:00Z",
            "metadata",
            Map.of("source", "test"));
    given()
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Insights-Key", "wrong-key")
        .body(request)
        .when()
        .post("/api/internal/insights/initiatives/start")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  public void acceptsValidKeyAndPersistsEvent() {
    String initiativeId = "internal-" + UUID.randomUUID();
    Map<String, Object> startRequest =
        Map.of(
            "initiativeId",
            initiativeId,
            "title",
            "Internal ingest test",
            "definitionStartedAt",
            "2026-03-07T00:00:00Z",
            "metadata",
            Map.of("source", "ci"));
    given()
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Insights-Key", INGEST_KEY)
        .body(startRequest)
        .when()
        .post("/api/internal/insights/initiatives/start")
        .then()
        .statusCode(202);

    Map<String, Object> appendRequest =
        Map.of(
            "initiativeId",
            initiativeId,
            "type",
            "PR_OPENED",
            "metadata",
            Map.of("pr", "123"));
    given()
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Insights-Key", INGEST_KEY)
        .body(appendRequest)
        .when()
        .post("/api/internal/insights/events")
        .then()
        .statusCode(202);

    List<Map<String, Object>> items =
        given()
            .accept(MediaType.APPLICATION_JSON)
            .when()
            .get("/api/private/admin/insights/initiatives?limit=200")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("$");

    assertTrue(items.stream().anyMatch(item -> initiativeId.equals(item.get("initiativeId"))));
  }
}
