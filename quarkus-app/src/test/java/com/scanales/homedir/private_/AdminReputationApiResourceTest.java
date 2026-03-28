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

  private void seedActivityLoopSignals(
      int publicProfileOpens, int boardProfileOpens, int feedbackSignals) {
    for (int i = 0; i < publicProfileOpens; i++) {
      usageMetricsService.recordFunnelStep("profile.public.open");
    }
    for (int i = 0; i < boardProfileOpens; i++) {
      usageMetricsService.recordFunnelStep("board_profile_open");
    }
    for (int i = 0; i < feedbackSignals; i++) {
      usageMetricsService.recordFunnelStep("community_vote");
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
    assertEquals(5L, ((Number) gaReadiness.get("minPublicProfileOpens")).longValue());
    assertEquals(5L, ((Number) gaReadiness.get("minBoardProfileOpens")).longValue());
    assertEquals(5L, ((Number) gaReadiness.get("minFeedbackSignals")).longValue());
    assertEquals(5L, ((Number) gaReadiness.get("minRecognitionSignals")).longValue());
    assertEquals(3L, ((Number) gaReadiness.get("minRecognitionValidators")).longValue());
    assertEquals(4L, ((Number) gaReadiness.get("minRecognitionTargets")).longValue());
    assertEquals(4L, ((Number) gaReadiness.get("minRecognitionSources")).longValue());
    assertEquals(70L, ((Number) gaReadiness.get("maxRecognitionValidatorSharePct")).longValue());
    assertEquals(7L, ((Number) gaReadiness.get("recognitionWindowDays")).longValue());
    assertEquals(false, gaReadiness.get("recognitionGateEnabled"));
    assertEquals(0L, ((Number) gaReadiness.get("recognitionSignals")).longValue());
    assertEquals(0L, ((Number) gaReadiness.get("recognitionValidators")).longValue());
    assertEquals(0L, ((Number) gaReadiness.get("recognitionTargets")).longValue());
    assertEquals(0L, ((Number) gaReadiness.get("recognitionSources")).longValue());
    assertEquals(0L, ((Number) gaReadiness.get("recognitionValidatorSharePct")).longValue());
    assertEquals(true, gaReadiness.get("snapshotRecorded"));
    assertEquals("critical_route_status", gaReadiness.get("primaryBlocker"));
    assertEquals("improve_critical_route_performance", gaReadiness.get("primaryAction"));
    @SuppressWarnings("unchecked")
    List<String> blockers = (List<String>) gaReadiness.get("blockers");
    assertTrue(blockers.contains("insufficient_samples"));
    assertTrue(blockers.contains("insufficient_live_traffic"));
    assertTrue(blockers.contains("insufficient_activity_loop_signals"));
    assertTrue(blockers.contains("insufficient_stability_windows"));
    assertTrue(blockers.contains("critical_route_status"));
    assertTrue(blockers.contains("active_worsening_trend"));
    assertFalse(blockers.contains("insufficient_recognition_signals"));
    assertFalse(blockers.contains("insufficient_recognition_validators"));
    assertFalse(blockers.contains("insufficient_recognition_targets"));
    assertFalse(blockers.contains("insufficient_recognition_sources"));
    assertFalse(blockers.contains("high_recognition_validator_concentration"));
    assertFalse(blockers.contains("stale_window_data"));

    @SuppressWarnings("unchecked")
    List<String> recommendedActions = (List<String>) gaReadiness.get("recommendedActions");
    assertTrue(recommendedActions.contains("collect_more_webvitals_samples"));
    assertTrue(recommendedActions.contains("increase_hub_route_adoption"));
    assertTrue(recommendedActions.contains("drive_profile_feedback_cycle"));
    assertTrue(recommendedActions.contains("observe_more_stable_windows"));
    assertTrue(recommendedActions.contains("improve_critical_route_performance"));
    assertTrue(recommendedActions.contains("triage_worsening_route"));
    assertFalse(recommendedActions.contains("increase_peer_recognition_activity"));
    assertFalse(recommendedActions.contains("expand_recognition_validator_pool"));
    assertFalse(recommendedActions.contains("broaden_recognition_reach"));
    assertFalse(recommendedActions.contains("diversify_recognition_sources"));
    assertFalse(recommendedActions.contains("distribute_recognition_activity"));

    @SuppressWarnings("unchecked")
    Map<String, Object> blockerDetails = (Map<String, Object>) gaReadiness.get("blockerDetails");
    assertTrue(blockerDetails.containsKey("insufficient_samples"));
    assertTrue(blockerDetails.containsKey("insufficient_live_traffic"));
    assertTrue(blockerDetails.containsKey("insufficient_activity_loop_signals"));
    assertTrue(blockerDetails.containsKey("insufficient_stability_windows"));
    assertTrue(blockerDetails.containsKey("critical_route_status"));
    assertTrue(blockerDetails.containsKey("active_worsening_trend"));
    assertFalse(blockerDetails.containsKey("insufficient_recognition_signals"));
    assertFalse(blockerDetails.containsKey("insufficient_recognition_validators"));
    assertFalse(blockerDetails.containsKey("insufficient_recognition_targets"));
    assertFalse(blockerDetails.containsKey("insufficient_recognition_sources"));
    assertFalse(blockerDetails.containsKey("high_recognition_validator_concentration"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> actionPlan = (List<Map<String, Object>>) gaReadiness.get("actionPlan");
    assertFalse(actionPlan.isEmpty());
    assertEquals("critical_route_status", actionPlan.get(0).get("blocker"));
    assertEquals("improve_critical_route_performance", actionPlan.get(0).get("action"));

    @SuppressWarnings("unchecked")
    Map<String, Object> rollout = (Map<String, Object>) payload.get("rollout");
    assertEquals("disabled", rollout.get("stage"));
    @SuppressWarnings("unchecked")
    Map<String, Object> decisionPack = (Map<String, Object>) payload.get("decisionPack");
    assertEquals("blocked", decisionPack.get("status"));
    assertEquals(false, decisionPack.get("automatedReady"));
    assertEquals("disabled", decisionPack.get("rolloutStage"));
    assertEquals("hold_rollout", decisionPack.get("recommendation"));
    assertEquals(3, ((Number) decisionPack.get("pendingManualChecksCount")).intValue());
    @SuppressWarnings("unchecked")
    Map<String, Object> closeoutPack = (Map<String, Object>) payload.get("closeoutPack");
    assertEquals("hold_rollout", closeoutPack.get("recommendation"));
    assertEquals("disabled", closeoutPack.get("rolloutStage"));
    assertEquals(3L, ((Number) closeoutPack.get("pendingChecks")).longValue());
    @SuppressWarnings("unchecked")
    Map<String, Object> runtime = (Map<String, Object>) closeoutPack.get("runtime");
    assertTrue(runtime.containsKey("version"));
    assertTrue(runtime.containsKey("commitId"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminGaReadinessIsReadyWhenRoutesAreHealthyAndStable() {
    seedLiveTraffic(12, 12);
    seedActivityLoopSignals(6, 6, 6);

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
    assertEquals(5L, ((Number) gaReadiness.get("minPublicProfileOpens")).longValue());
    assertEquals(5L, ((Number) gaReadiness.get("minBoardProfileOpens")).longValue());
    assertEquals(5L, ((Number) gaReadiness.get("minFeedbackSignals")).longValue());
    assertEquals(5L, ((Number) gaReadiness.get("minRecognitionSignals")).longValue());
    assertEquals(3L, ((Number) gaReadiness.get("minRecognitionValidators")).longValue());
    assertEquals(4L, ((Number) gaReadiness.get("minRecognitionTargets")).longValue());
    assertEquals(4L, ((Number) gaReadiness.get("minRecognitionSources")).longValue());
    assertEquals(70L, ((Number) gaReadiness.get("maxRecognitionValidatorSharePct")).longValue());
    assertEquals(7L, ((Number) gaReadiness.get("recognitionWindowDays")).longValue());
    assertEquals(false, gaReadiness.get("recognitionGateEnabled"));
    assertEquals(0L, ((Number) gaReadiness.get("recognitionSignals")).longValue());
    assertEquals(0L, ((Number) gaReadiness.get("recognitionValidators")).longValue());
    assertEquals(0L, ((Number) gaReadiness.get("recognitionTargets")).longValue());
    assertEquals(0L, ((Number) gaReadiness.get("recognitionSources")).longValue());
    assertEquals(0L, ((Number) gaReadiness.get("recognitionValidatorSharePct")).longValue());
    assertEquals(true, gaReadiness.get("snapshotRecorded"));
    assertEquals("none", gaReadiness.get("primaryBlocker"));
    assertEquals("none", gaReadiness.get("primaryAction"));
    @SuppressWarnings("unchecked")
    List<String> blockers = (List<String>) gaReadiness.get("blockers");
    assertTrue(blockers.isEmpty());
    @SuppressWarnings("unchecked")
    List<String> recommendedActions = (List<String>) gaReadiness.get("recommendedActions");
    assertTrue(recommendedActions.isEmpty());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> actionPlan = (List<Map<String, Object>>) gaReadiness.get("actionPlan");
    assertTrue(actionPlan.isEmpty());
    @SuppressWarnings("unchecked")
    Map<String, Object> blockerDetails = (Map<String, Object>) gaReadiness.get("blockerDetails");
    assertTrue(blockerDetails.isEmpty());

    @SuppressWarnings("unchecked")
    Map<String, Object> activityLoop = (Map<String, Object>) gaReadiness.get("activityLoop");
    assertTrue(((Number) activityLoop.get("publicProfileOpens")).longValue() >= 5L);
    assertTrue(((Number) activityLoop.get("boardProfileOpens")).longValue() >= 5L);
    assertTrue(((Number) activityLoop.get("feedbackSignals")).longValue() >= 5L);

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

    @SuppressWarnings("unchecked")
    Map<String, Object> rollout = (Map<String, Object>) payload.get("rollout");
    assertEquals("disabled", rollout.get("stage"));
    @SuppressWarnings("unchecked")
    Map<String, Object> measurement = (Map<String, Object>) payload.get("measurement");
    assertEquals("fresh", measurement.get("freshnessStatus"));
    assertEquals(true, measurement.get("snapshotRecorded"));
    assertEquals(2L, ((Number) measurement.get("readyRoutes")).longValue());
    assertEquals(2L, ((Number) measurement.get("totalRoutes")).longValue());
    @SuppressWarnings("unchecked")
    Map<String, Object> decisionPack = (Map<String, Object>) payload.get("decisionPack");
    assertEquals("ready", decisionPack.get("status"));
    assertEquals(true, decisionPack.get("automatedReady"));
    assertEquals("disabled", decisionPack.get("rolloutStage"));
    assertEquals("enable_public_nav", decisionPack.get("recommendation"));
    assertEquals(3, ((Number) decisionPack.get("pendingManualChecksCount")).intValue());
    @SuppressWarnings("unchecked")
    Map<String, Object> closeoutPack = (Map<String, Object>) payload.get("closeoutPack");
    assertEquals("enable_public_nav", closeoutPack.get("recommendation"));
    assertEquals("disabled", closeoutPack.get("rolloutStage"));
    assertEquals(2L, ((Number) closeoutPack.get("pendingChecks")).longValue());
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminGaReadinessBlocksWhenSnapshotIsStale() {
    seedLiveTraffic(12, 12);
    seedActivityLoopSignals(6, 6, 6);

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
    assertEquals(false, gaReadinessStale.get("recognitionGateEnabled"));
    assertEquals(0L, ((Number) gaReadinessStale.get("recognitionSignals")).longValue());
    assertEquals(0L, ((Number) gaReadinessStale.get("recognitionValidators")).longValue());
    assertEquals(0L, ((Number) gaReadinessStale.get("recognitionTargets")).longValue());
    assertEquals(0L, ((Number) gaReadinessStale.get("recognitionSources")).longValue());
    assertEquals(0L, ((Number) gaReadinessStale.get("recognitionValidatorSharePct")).longValue());
    assertEquals("stale_window_data", gaReadinessStale.get("primaryBlocker"));
    assertEquals("verify_web_vitals_ingestion", gaReadinessStale.get("primaryAction"));
    @SuppressWarnings("unchecked")
    List<String> blockers = (List<String>) gaReadinessStale.get("blockers");
    assertTrue(blockers.contains("stale_window_data"));
    assertFalse(blockers.contains("insufficient_live_traffic"));
    assertFalse(blockers.contains("insufficient_activity_loop_signals"));
    assertFalse(blockers.contains("insufficient_recognition_signals"));
    assertFalse(blockers.contains("insufficient_recognition_validators"));
    assertFalse(blockers.contains("insufficient_recognition_targets"));
    assertFalse(blockers.contains("insufficient_recognition_sources"));
    assertFalse(blockers.contains("high_recognition_validator_concentration"));
    @SuppressWarnings("unchecked")
    List<String> recommendedActions = (List<String>) gaReadinessStale.get("recommendedActions");
    assertTrue(recommendedActions.contains("verify_web_vitals_ingestion"));
    assertFalse(recommendedActions.contains("drive_profile_feedback_cycle"));
    assertFalse(recommendedActions.contains("increase_peer_recognition_activity"));
    assertFalse(recommendedActions.contains("expand_recognition_validator_pool"));
    assertFalse(recommendedActions.contains("broaden_recognition_reach"));
    assertFalse(recommendedActions.contains("diversify_recognition_sources"));
    assertFalse(recommendedActions.contains("distribute_recognition_activity"));
    @SuppressWarnings("unchecked")
    Map<String, Object> blockerDetails = (Map<String, Object>) gaReadinessStale.get("blockerDetails");
    assertTrue(blockerDetails.containsKey("stale_window_data"));
    assertFalse(blockerDetails.containsKey("insufficient_recognition_validators"));
    assertFalse(blockerDetails.containsKey("insufficient_recognition_targets"));
    assertFalse(blockerDetails.containsKey("insufficient_recognition_sources"));
    assertFalse(blockerDetails.containsKey("high_recognition_validator_concentration"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> actionPlan = (List<Map<String, Object>>) gaReadinessStale.get("actionPlan");
    assertFalse(actionPlan.isEmpty());
    assertEquals("stale_window_data", actionPlan.get(0).get("blocker"));
    assertEquals("verify_web_vitals_ingestion", actionPlan.get(0).get("action"));
    @SuppressWarnings("unchecked")
    Map<String, Object> measurement = (Map<String, Object>) payload.get("measurement");
    assertEquals("stale", measurement.get("freshnessStatus"));
    assertEquals(false, measurement.get("snapshotRecorded"));
    assertEquals(2L, ((Number) measurement.get("readyRoutes")).longValue());
    assertEquals(2L, ((Number) measurement.get("totalRoutes")).longValue());
    @SuppressWarnings("unchecked")
    Map<String, Object> closeoutPack = (Map<String, Object>) payload.get("closeoutPack");
    assertEquals("hold_rollout", closeoutPack.get("recommendation"));
    assertEquals(3L, ((Number) closeoutPack.get("pendingChecks")).longValue());
  }
}
