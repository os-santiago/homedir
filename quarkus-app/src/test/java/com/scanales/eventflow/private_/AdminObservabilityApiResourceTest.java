package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.eventflow.TestDataDir;
import com.scanales.eventflow.insights.DevelopmentInsightsLedgerService;
import com.scanales.eventflow.service.UsageMetricsService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
class AdminObservabilityApiResourceTest {

  @Inject UsageMetricsService usageMetricsService;

  @Inject DevelopmentInsightsLedgerService insightsLedgerService;

  @BeforeEach
  void setUp() {
    usageMetricsService.reset();
  }

  @Test
  @TestSecurity(user = "alice")
  void nonAdminCannotAccessDashboard() {
    given().when().get("/api/private/admin/observability/dashboard").then().statusCode(403);
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void dashboardCombinesUsageAndInsightsSignals() {
    usageMetricsService.recordPageView("/comunidad", "session-a", "Mozilla/5.0");
    usageMetricsService.recordPageView("/eventos", "session-b", "Mozilla/5.0");
    usageMetricsService.recordPageView("/event/devopsdays-santiago-2026", "session-c", "Mozilla/5.0");
    usageMetricsService.recordEventView("devopsdays-santiago-2026", "session-c", "Mozilla/5.0");
    usageMetricsService.recordTalkView("dod-2026-day1-welcome", "session-d", "Mozilla/5.0");
    usageMetricsService.recordTalkRegister("dod-2026-day1-welcome", List.of(), "Mozilla/5.0");
    usageMetricsService.recordFunnelStep("community_vote");
    usageMetricsService.recordFunnelStep("cfp_submit");
    usageMetricsService.recordFunnelStep("volunteer_submit");

    String initiativeId = "obs-" + UUID.randomUUID();
    insightsLedgerService.startInitiative(
        initiativeId,
        "Business Observability Test",
        Instant.now().minusSeconds(3600).toString(),
        Map.of("source", "test"));
    insightsLedgerService.append(initiativeId, "PR_VALIDATION_PASSED", Map.of("source", "test"));
    insightsLedgerService.append(initiativeId, "PRODUCTION_VERIFIED", Map.of("source", "test"));

    Map<String, Object> payload =
        given()
            .when()
            .get("/api/private/admin/observability/dashboard?hours=24")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("$");

    assertTrue(((Number) payload.get("interactionsLastWindow")).longValue() >= 3L);
    assertTrue(((Number) payload.get("activeModules")).longValue() >= 2L);
    assertTrue(payload.get("deliveryPulse") instanceof Map);
    Map<?, ?> deliveryPulse = (Map<?, ?>) payload.get("deliveryPulse");
    assertTrue(((Number) deliveryPulse.get("eventsLast24Hours")).longValue() >= 1L);
    assertTrue(((Number) deliveryPulse.get("activeInitiativesLast24Hours")).longValue() >= 1L);
    assertTrue(payload.get("heatmap") instanceof List);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> heatmap = (List<Map<String, Object>>) payload.get("heatmap");
    assertFalse(heatmap.isEmpty());
    assertTrue(heatmap.stream().anyMatch(row -> "events".equals(row.get("code"))));
    assertTrue(heatmap.stream().anyMatch(row -> "community".equals(row.get("code"))));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> hotActions = (List<Map<String, Object>>) payload.get("hotActions");
    assertTrue(hotActions.stream().anyMatch(row -> "community_vote".equals(row.get("code"))));
    assertTrue(hotActions.stream().anyMatch(row -> "cfp_submit".equals(row.get("code"))));
  }
}
