package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
  @TestSecurity(user = "alice")
  public void nonAdminCannotExportCsv() {
    given().when().get("/api/private/admin/insights/initiatives/export.csv").then().statusCode(403);
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
    appendEvent(initiativeId, "PR_VALIDATION_PASSED");
    appendEvent(initiativeId, "PR_VALIDATION_FAILED");
    appendEvent(initiativeId, "PR_MERGED");
    appendEvent(initiativeId, "PRODUCTION_RELEASE_FAILED");
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
    assertTrue(((Number) status.get("prValidationPassedEvents")).intValue() >= 1);
    assertTrue(((Number) status.get("prValidationFailedEvents")).intValue() >= 1);
    assertTrue(((Number) status.get("productionReleaseFailedEvents")).intValue() >= 1);
    assertTrue(((Number) status.get("prValidationTotalEvents")).intValue() >= 2);
    assertTrue(((Number) status.get("productionOutcomeEvents")).intValue() >= 2);
    assertTrue(((Number) status.get("prValidationSuccessRatePct")).longValue() >= 0L);
    assertTrue(((Number) status.get("prValidationSuccessRatePct")).longValue() <= 100L);
    assertTrue(((Number) status.get("prValidationPassedEventsLast7Days")).intValue() >= 1);
    assertTrue(((Number) status.get("prValidationFailedEventsLast7Days")).intValue() >= 1);
    assertTrue(((Number) status.get("prValidationSuccessRatePctLast7Days")).longValue() >= 0L);
    assertTrue(((Number) status.get("prValidationSuccessRatePctLast7Days")).longValue() <= 100L);
    assertTrue(((Number) status.get("productionSuccessRatePct")).longValue() >= 0L);
    assertTrue(((Number) status.get("productionSuccessRatePct")).longValue() <= 100L);
    assertTrue(((Number) status.get("productionVerifiedEventsLast7Days")).intValue() >= 1);
    assertTrue(((Number) status.get("productionReleaseFailedEventsLast7Days")).intValue() >= 1);
    assertTrue(((Number) status.get("productionSuccessRatePctLast7Days")).longValue() >= 0L);
    assertTrue(((Number) status.get("productionSuccessRatePctLast7Days")).longValue() <= 100L);
    assertTrue(((Number) status.get("eventsLast7Days")).intValue() >= 1);
    assertTrue(((Number) status.get("eventsPrevious7Days")).intValue() >= 0);
    Object eventsTrend = status.get("eventsTrendPct");
    if (eventsTrend != null) {
      assertTrue(eventsTrend instanceof Number);
    }
    assertTrue(((Number) status.get("activeInitiativesLast7Days")).intValue() >= 1);
    Object topTypes = status.get("topEventTypesLast7Days");
    assertTrue(topTypes instanceof Map);
    assertTrue(((Map<?, ?>) topTypes).containsKey("INITIATIVE_STARTED"));
    assertTrue(((Number) status.get("minutesSinceLastEvent")).longValue() >= 0L);
    Object stale = status.get("stale");
    assertTrue(stale instanceof Boolean);
    assertTrue(((Number) status.get("avgLeadHoursToMerge")).longValue() >= 0L);
    assertTrue(((Number) status.get("avgLeadHoursToProduction")).longValue() >= 0L);
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  public void initiativesSupportOffsetPagination() {
    String initiativeA = "page-a-" + UUID.randomUUID();
    String initiativeB = "page-b-" + UUID.randomUUID();
    String initiativeC = "page-c-" + UUID.randomUUID();

    startInitiative(initiativeA, "2026-03-01T00:00:00Z");
    startInitiative(initiativeB, "2026-03-02T00:00:00Z");
    startInitiative(initiativeC, "2026-03-03T00:00:00Z");

    List<Map<String, Object>> page0 =
        given()
            .accept(MediaType.APPLICATION_JSON)
            .when()
            .get("/api/private/admin/insights/initiatives?limit=1&offset=0")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("$");

    List<Map<String, Object>> page1 =
        given()
            .accept(MediaType.APPLICATION_JSON)
            .when()
            .get("/api/private/admin/insights/initiatives?limit=1&offset=1")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("$");

    assertEquals(1, page0.size());
    assertEquals(1, page1.size());
    String firstId = String.valueOf(page0.get(0).get("initiativeId"));
    String secondId = String.valueOf(page1.get(0).get("initiativeId"));
    assertTrue(!firstId.equals(secondId));
  }

  private static void startInitiative(String initiativeId, String definitionStartedAt) {
    Map<String, Object> startRequest =
        Map.of(
            "initiativeId",
            initiativeId,
            "title",
            "Pagination Test " + initiativeId,
            "definitionStartedAt",
            definitionStartedAt,
            "metadata",
            Map.of("source", "test"));
    given()
        .contentType(MediaType.APPLICATION_JSON)
        .body(startRequest)
        .when()
        .post("/api/private/admin/insights/initiatives/start")
        .then()
        .statusCode(201);
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

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  public void csvExportIncludesHeaderAndRows() {
    String initiativeId = "csv-" + UUID.randomUUID();
    startInitiative(initiativeId, "2026-03-04T00:00:00Z");
    appendEvent(initiativeId, "PR_OPENED");

    String csv =
        given()
            .accept("text/csv")
            .when()
            .get("/api/private/admin/insights/initiatives/export.csv?limit=200&offset=0")
            .then()
            .statusCode(200)
            .extract()
            .asString();

    assertTrue(csv.contains("initiative_id,title,state"));
    assertTrue(csv.contains(initiativeId));
  }
}
