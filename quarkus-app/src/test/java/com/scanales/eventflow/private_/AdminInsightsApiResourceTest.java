package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class AdminInsightsApiResourceTest {

  @Test
  @TestSecurity(user = "alice")
  public void nonAdminCannotAccessStatus() {
    given().when().get("/api/private/admin/insights/status").then().statusCode(403);
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  public void initiativesIncludeLeadKpis() {
    String initiativeId = "kpi-" + UUID.randomUUID();
    Map<String, Object> startRequest =
        Map.of(
            "initiativeId",
            initiativeId,
            "title",
            "KPI Summary Test",
            "definitionStartedAt",
            "2026-03-01T00:00:00Z",
            "metadata",
            Map.of("source", "test"));

    given()
        .contentType(MediaType.APPLICATION_JSON)
        .body(startRequest)
        .when()
        .post("/api/private/admin/insights/initiatives/start")
        .then()
        .statusCode(201);

    appendEvent(initiativeId, "PR_OPENED");
    appendEvent(initiativeId, "PR_MERGED");
    appendEvent(initiativeId, "PRODUCTION_VERIFIED");

    List<Map<String, Object>> initiatives =
        given()
            .accept(MediaType.APPLICATION_JSON)
            .when()
            .get("/api/private/admin/insights/initiatives?limit=200")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("$");

    Map<String, Object> summary =
        initiatives.stream()
            .filter(item -> initiativeId.equals(item.get("initiativeId")))
            .findFirst()
            .orElseThrow();

    assertNotNull(summary.get("definitionStartedAt"));
    assertNotNull(summary.get("prOpenedAt"));
    assertNotNull(summary.get("prMergedAt"));
    assertNotNull(summary.get("productionVerifiedAt"));
    assertTrue(((Number) summary.get("leadHoursToMerge")).longValue() >= 0L);
    assertTrue(((Number) summary.get("leadHoursToProduction")).longValue() >= 0L);

    Map<String, Object> status =
        given()
            .accept(MediaType.APPLICATION_JSON)
            .when()
            .get("/api/private/admin/insights/status")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("$");
    assertTrue(((Number) status.get("startedInitiatives")).intValue() >= 1);
    assertTrue(((Number) status.get("mergedInitiatives")).intValue() >= 1);
    assertTrue(((Number) status.get("productionVerifiedInitiatives")).intValue() >= 1);
  }

  private static void appendEvent(String initiativeId, String type) {
    Map<String, Object> request = Map.of("initiativeId", initiativeId, "type", type, "metadata", Map.of());
    given()
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .when()
        .post("/api/private/admin/insights/events")
        .then()
        .statusCode(201);
  }
}
