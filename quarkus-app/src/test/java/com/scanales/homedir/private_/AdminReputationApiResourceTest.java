package com.scanales.homedir.private_;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.TestDataDir;
import com.scanales.homedir.service.UsageMetricsService;
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
@TestProfile(AdminReputationApiResourceTest.Profile.class)
class AdminReputationApiResourceTest {

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("metrics.buffer.max-size", "200");
    }
  }

  @Inject UsageMetricsService usageMetricsService;

  @BeforeEach
  void setUp() {
    usageMetricsService.reset();
  }

  private void seedLiveTraffic(int hubViews, int howViews) {
    for (int i = 0; i < hubViews; i++) {
      usageMetricsService.recordPageView("/comunidad/reputation-hub", "Mozilla/5.0");
    }
    for (int i = 0; i < howViews; i++) {
      usageMetricsService.recordPageView("/comunidad/reputation-hub/how", "Mozilla/5.0");
    }
  }

  @Test
  @TestSecurity(user = "alice")
  void nonAdminCannotAccessPhase0() {
    given().when().get("/api/private/admin/reputation/phase0").then().statusCode(403);
    given().when().get("/api/private/admin/reputation/web-vitals").then().statusCode(403);
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanAccessPhase0BaselineSnapshot() {
    usageMetricsService.recordPageView("/comunidad/board", "session-a", "Mozilla/5.0");
    usageMetricsService.recordPageView("/comunidad/board/github-users", "session-b", "Mozilla/5.0");
    usageMetricsService.recordPageView("/private/profile", "session-c", "Mozilla/5.0");
    usageMetricsService.recordFunnelStep("profile.public.open");
    usageMetricsService.recordFunnelStep("board_profile_open");
    usageMetricsService.recordFunnelStep("community.vote.recommended");

    Map<String, Object> payload =
        given()
            .when()
            .get("/api/private/admin/reputation/phase0")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("$");

    Map<?, ?> flags = (Map<?, ?>) payload.get("flags");
    assertFalse((Boolean) flags.get("engineEnabled"));
    assertFalse((Boolean) flags.get("hubUiEnabled"));
    assertFalse((Boolean) flags.get("recognitionEnabled"));
    assertFalse((Boolean) flags.get("shadowReadEnabled"));
    assertFalse((Boolean) flags.get("profileSummaryEnabled"));
    assertTrue(((Number) payload.get("communityBoardViews")).longValue() >= 2L);
    assertTrue(((Number) payload.get("profileViews")).longValue() >= 1L);
    assertTrue(((Number) payload.get("publicProfileOpens")).longValue() >= 1L);
    assertTrue(((Number) payload.get("boardProfileOpens")).longValue() >= 1L);
    assertTrue(((Number) payload.get("recommendationSignals")).longValue() >= 1L);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> taxonomy = (List<Map<String, Object>>) payload.get("taxonomy");
    assertTrue(taxonomy.stream().anyMatch(row -> "quest_completed".equals(row.get("eventType"))));
    assertTrue(taxonomy.stream().anyMatch(row -> "content_recommended".equals(row.get("eventType"))));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminGetsConflictWhenPhase2ShadowReadIsDisabled() {
    given().when().get("/api/private/admin/reputation/phase2/diagnostics").then().statusCode(409);
    given().when().get("/api/private/admin/reputation/phase2/user/alice@example.com").then().statusCode(409);
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminCanReadReputationWebVitalsSummary() {
    usageMetricsService.recordFunnelStep("reputation.hub.webvitals.sample");
    usageMetricsService.recordFunnelStep("reputation.hub.webvitals.device.mobile");
    usageMetricsService.recordFunnelStep("reputation.hub.webvitals.lcp.needs_improvement");
    usageMetricsService.recordFunnelStep("reputation.hub.webvitals.inp.poor");

    usageMetricsService.recordFunnelStep("reputation.how.webvitals.sample");
    usageMetricsService.recordFunnelStep("reputation.how.webvitals.device.desktop");
    usageMetricsService.recordFunnelStep("reputation.how.webvitals.lcp.good");
    usageMetricsService.recordFunnelStep("reputation.how.webvitals.inp.good");

    given().when().get("/api/private/admin/reputation/web-vitals").then().statusCode(200);

    usageMetricsService.recordFunnelStep("reputation.hub.webvitals.sample");
    usageMetricsService.recordFunnelStep("reputation.hub.webvitals.lcp.poor");
    usageMetricsService.recordFunnelStep("reputation.hub.webvitals.inp.poor");

    usageMetricsService.recordFunnelStep("reputation.how.webvitals.sample");
    usageMetricsService.recordFunnelStep("reputation.how.webvitals.lcp.good");
    usageMetricsService.recordFunnelStep("reputation.how.webvitals.inp.good");

    Map<String, Object> payload =
        given()
            .when()
            .get("/api/private/admin/reputation/web-vitals")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("$");

    assertTrue(((Number) payload.get("totalSamples")).longValue() >= 2L);
    @SuppressWarnings("unchecked")
    Map<String, Object> routes = (Map<String, Object>) payload.get("routes");
    @SuppressWarnings("unchecked")
    Map<String, Object> hub = (Map<String, Object>) routes.get("hub");
    @SuppressWarnings("unchecked")
    Map<String, Object> how = (Map<String, Object>) routes.get("how");
    assertTrue(((Number) hub.get("samples")).longValue() >= 1L);
    assertTrue(((Number) how.get("samples")).longValue() >= 1L);

    @SuppressWarnings("unchecked")
    Map<String, Object> hubInp = (Map<String, Object>) hub.get("inp");
    @SuppressWarnings("unchecked")
    Map<String, Object> howLcp = (Map<String, Object>) how.get("lcp");
    assertTrue(((Number) hubInp.get("poor")).longValue() >= 1L);
    assertTrue(((Number) howLcp.get("good")).longValue() >= 1L);

    assertTrue("hub".equals(payload.get("nextFocusRoute")));
    @SuppressWarnings("unchecked")
    Map<String, Object> assessments = (Map<String, Object>) payload.get("assessments");
    @SuppressWarnings("unchecked")
    Map<String, Object> hubAssessment = (Map<String, Object>) assessments.get("hub");
    @SuppressWarnings("unchecked")
    Map<String, Object> howAssessment = (Map<String, Object>) assessments.get("how");
    assertTrue(((Number) hubAssessment.get("overallScore")).intValue() < 60);
    assertTrue("critical".equals(hubAssessment.get("status")));
    assertTrue(((Number) howAssessment.get("overallScore")).intValue() >= 90);

    @SuppressWarnings("unchecked")
    Map<String, Object> trend = (Map<String, Object>) payload.get("trend");
    assertTrue(((Number) trend.get("windowSize")).longValue() >= 2L);
    @SuppressWarnings("unchecked")
    Map<String, Object> trendRoutes = (Map<String, Object>) trend.get("routes");
    @SuppressWarnings("unchecked")
    Map<String, Object> hubTrend = (Map<String, Object>) trendRoutes.get("hub");
    @SuppressWarnings("unchecked")
    Map<String, Object> howTrend = (Map<String, Object>) trendRoutes.get("how");
    assertTrue(((Number) hubTrend.get("samplesDelta")).longValue() >= 1L);
    assertTrue("worsening".equals(hubTrend.get("status")));
    assertTrue("improving".equals(howTrend.get("status")));

    @SuppressWarnings("unchecked")
    Map<String, Object> gaReadiness = (Map<String, Object>) payload.get("gaReadiness");
    assertEquals("not_ready", gaReadiness.get("status"));
    assertEquals(20L, ((Number) gaReadiness.get("minRouteSamples")).longValue());
    assertEquals(3L, ((Number) gaReadiness.get("minStableWindows")).longValue());
    assertEquals(10L, ((Number) gaReadiness.get("minRoutePageViews")).longValue());
    assertEquals(true, gaReadiness.get("snapshotRecorded"));
    @SuppressWarnings("unchecked")
    List<String> blockers = (List<String>) gaReadiness.get("blockers");
    assertTrue(blockers.contains("insufficient_samples"));
    assertTrue(blockers.contains("insufficient_live_traffic"));
    assertTrue(blockers.contains("insufficient_stability_windows"));
    assertTrue(blockers.contains("critical_route_status"));
    assertTrue(blockers.contains("active_worsening_trend"));
    assertFalse(blockers.contains("stale_window_data"));

    @SuppressWarnings("unchecked")
    List<String> recommendedActions = (List<String>) gaReadiness.get("recommendedActions");
    assertTrue(recommendedActions.contains("collect_more_webvitals_samples"));
    assertTrue(recommendedActions.contains("increase_hub_route_adoption"));
    assertTrue(recommendedActions.contains("observe_more_stable_windows"));
    assertTrue(recommendedActions.contains("improve_critical_route_performance"));
    assertTrue(recommendedActions.contains("triage_worsening_route"));

    @SuppressWarnings("unchecked")
    Map<String, Object> blockerDetails = (Map<String, Object>) gaReadiness.get("blockerDetails");
    assertTrue(blockerDetails.containsKey("insufficient_samples"));
    assertTrue(blockerDetails.containsKey("insufficient_live_traffic"));
    assertTrue(blockerDetails.containsKey("insufficient_stability_windows"));
    assertTrue(blockerDetails.containsKey("critical_route_status"));
    assertTrue(blockerDetails.containsKey("active_worsening_trend"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminGaReadinessIsReadyWhenRoutesAreHealthyAndStable() {
    seedLiveTraffic(12, 12);

    for (int i = 0; i < 22; i++) {
      usageMetricsService.recordFunnelStep("reputation.hub.webvitals.sample");
      usageMetricsService.recordFunnelStep("reputation.hub.webvitals.lcp.good");
      usageMetricsService.recordFunnelStep("reputation.hub.webvitals.inp.good");
      usageMetricsService.recordFunnelStep("reputation.how.webvitals.sample");
      usageMetricsService.recordFunnelStep("reputation.how.webvitals.lcp.good");
      usageMetricsService.recordFunnelStep("reputation.how.webvitals.inp.good");
    }

    Map<String, Object> payload =
        given()
            .when()
            .get("/api/private/admin/reputation/web-vitals")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("$");

    for (int i = 0; i < 3; i++) {
      usageMetricsService.recordFunnelStep("reputation.hub.webvitals.sample");
      usageMetricsService.recordFunnelStep("reputation.hub.webvitals.lcp.good");
      usageMetricsService.recordFunnelStep("reputation.hub.webvitals.inp.good");
      usageMetricsService.recordFunnelStep("reputation.how.webvitals.sample");
      usageMetricsService.recordFunnelStep("reputation.how.webvitals.lcp.good");
      usageMetricsService.recordFunnelStep("reputation.how.webvitals.inp.good");
      payload =
          given()
              .when()
              .get("/api/private/admin/reputation/web-vitals")
              .then()
              .statusCode(200)
              .extract()
              .jsonPath()
              .getMap("$");
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> gaReadiness = (Map<String, Object>) payload.get("gaReadiness");
    assertEquals("ready", gaReadiness.get("status"));
    assertEquals(3L, ((Number) gaReadiness.get("minStableWindows")).longValue());
    assertEquals(10L, ((Number) gaReadiness.get("minRoutePageViews")).longValue());
    assertEquals(true, gaReadiness.get("snapshotRecorded"));
    @SuppressWarnings("unchecked")
    List<String> blockers = (List<String>) gaReadiness.get("blockers");
    assertTrue(blockers.isEmpty());
    @SuppressWarnings("unchecked")
    List<String> recommendedActions = (List<String>) gaReadiness.get("recommendedActions");
    assertTrue(recommendedActions.isEmpty());
    @SuppressWarnings("unchecked")
    Map<String, Object> blockerDetails = (Map<String, Object>) gaReadiness.get("blockerDetails");
    assertTrue(blockerDetails.isEmpty());

    @SuppressWarnings("unchecked")
    Map<String, Object> gaRoutes = (Map<String, Object>) gaReadiness.get("routes");
    @SuppressWarnings("unchecked")
    Map<String, Object> hubGa = (Map<String, Object>) gaRoutes.get("hub");
    @SuppressWarnings("unchecked")
    Map<String, Object> howGa = (Map<String, Object>) gaRoutes.get("how");
    assertEquals("healthy", hubGa.get("assessmentStatus"));
    assertEquals("healthy", howGa.get("assessmentStatus"));
    assertEquals("improving", hubGa.get("trendStatus"));
    assertEquals("improving", howGa.get("trendStatus"));

    @SuppressWarnings("unchecked")
    Map<String, Object> stability = (Map<String, Object>) gaReadiness.get("stability");
    @SuppressWarnings("unchecked")
    Map<String, Object> hubStability = (Map<String, Object>) stability.get("hub");
    @SuppressWarnings("unchecked")
    Map<String, Object> howStability = (Map<String, Object>) stability.get("how");
    assertTrue(((Number) hubStability.get("observedWindows")).longValue() >= 3L);
    assertTrue(((Number) howStability.get("observedWindows")).longValue() >= 3L);
    assertTrue(((Number) hubStability.get("consecutiveNonWorsening")).longValue() >= 3L);
    assertTrue(((Number) howStability.get("consecutiveNonWorsening")).longValue() >= 3L);
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminGaReadinessBlocksWhenSnapshotIsStale() {
    seedLiveTraffic(12, 12);

    for (int i = 0; i < 22; i++) {
      usageMetricsService.recordFunnelStep("reputation.hub.webvitals.sample");
      usageMetricsService.recordFunnelStep("reputation.hub.webvitals.lcp.good");
      usageMetricsService.recordFunnelStep("reputation.hub.webvitals.inp.good");
      usageMetricsService.recordFunnelStep("reputation.how.webvitals.sample");
      usageMetricsService.recordFunnelStep("reputation.how.webvitals.lcp.good");
      usageMetricsService.recordFunnelStep("reputation.how.webvitals.inp.good");
    }

    Map<String, Object> payload =
        given()
            .when()
            .get("/api/private/admin/reputation/web-vitals")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("$");

    for (int i = 0; i < 3; i++) {
      usageMetricsService.recordFunnelStep("reputation.hub.webvitals.sample");
      usageMetricsService.recordFunnelStep("reputation.hub.webvitals.lcp.good");
      usageMetricsService.recordFunnelStep("reputation.hub.webvitals.inp.good");
      usageMetricsService.recordFunnelStep("reputation.how.webvitals.sample");
      usageMetricsService.recordFunnelStep("reputation.how.webvitals.lcp.good");
      usageMetricsService.recordFunnelStep("reputation.how.webvitals.inp.good");
      payload =
          given()
              .when()
              .get("/api/private/admin/reputation/web-vitals")
              .then()
              .statusCode(200)
              .extract()
              .jsonPath()
              .getMap("$");
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> gaReadinessReady = (Map<String, Object>) payload.get("gaReadiness");
    assertEquals("ready", gaReadinessReady.get("status"));
    assertEquals(true, gaReadinessReady.get("snapshotRecorded"));

    payload =
        given()
            .when()
            .get("/api/private/admin/reputation/web-vitals")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("$");

    @SuppressWarnings("unchecked")
    Map<String, Object> gaReadinessStale = (Map<String, Object>) payload.get("gaReadiness");
    assertEquals("not_ready", gaReadinessStale.get("status"));
    assertEquals(false, gaReadinessStale.get("snapshotRecorded"));
    @SuppressWarnings("unchecked")
    List<String> blockers = (List<String>) gaReadinessStale.get("blockers");
    assertTrue(blockers.contains("stale_window_data"));
    assertFalse(blockers.contains("insufficient_live_traffic"));
    @SuppressWarnings("unchecked")
    List<String> recommendedActions = (List<String>) gaReadinessStale.get("recommendedActions");
    assertTrue(recommendedActions.contains("verify_web_vitals_ingestion"));
    @SuppressWarnings("unchecked")
    Map<String, Object> blockerDetails = (Map<String, Object>) gaReadinessStale.get("blockerDetails");
    assertTrue(blockerDetails.containsKey("stale_window_data"));
  }
}
