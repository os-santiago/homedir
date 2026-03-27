package com.scanales.homedir.private_;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.TestDataDir;
import com.scanales.homedir.reputation.ReputationEngineService;
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
@TestProfile(AdminReputationApiRecognitionGateTest.Profile.class)
class AdminReputationApiRecognitionGateTest {

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "metrics.buffer.max-size",
          "200",
          "reputation.engine.enabled",
          "true",
          "reputation.recognition.enabled",
          "true");
    }
  }

  @Inject UsageMetricsService usageMetricsService;
  @Inject ReputationEngineService reputationEngineService;

  @BeforeEach
  void setUp() {
    usageMetricsService.reset();
    reputationEngineService.resetForTests();
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void recognitionGateBlocksWhenSignalsAreBelowThreshold() {
    Map<String, Object> payload = prepareStableReadinessBaseline();

    @SuppressWarnings("unchecked")
    Map<String, Object> gaReadiness = (Map<String, Object>) payload.get("gaReadiness");
    assertEquals("not_ready", gaReadiness.get("status"));
    assertEquals(true, gaReadiness.get("recognitionGateEnabled"));
    assertEquals(5L, ((Number) gaReadiness.get("minRecognitionSignals")).longValue());
    assertEquals(3L, ((Number) gaReadiness.get("minRecognitionValidators")).longValue());
    assertEquals(70L, ((Number) gaReadiness.get("maxRecognitionValidatorSharePct")).longValue());
    assertEquals(0L, ((Number) gaReadiness.get("recognitionSignals")).longValue());
    assertEquals(0L, ((Number) gaReadiness.get("recognitionValidators")).longValue());
    assertEquals(0L, ((Number) gaReadiness.get("recognitionValidatorSharePct")).longValue());
    assertEquals("insufficient_recognition_signals", gaReadiness.get("primaryBlocker"));
    assertEquals("increase_peer_recognition_activity", gaReadiness.get("primaryAction"));

    @SuppressWarnings("unchecked")
    List<String> blockers = (List<String>) gaReadiness.get("blockers");
    assertTrue(blockers.contains("insufficient_recognition_signals"));
    assertTrue(blockers.contains("insufficient_recognition_validators"));
    assertFalse(blockers.contains("high_recognition_validator_concentration"));

    @SuppressWarnings("unchecked")
    List<String> recommendedActions = (List<String>) gaReadiness.get("recommendedActions");
    assertTrue(recommendedActions.contains("increase_peer_recognition_activity"));
    assertTrue(recommendedActions.contains("expand_recognition_validator_pool"));

    @SuppressWarnings("unchecked")
    Map<String, Object> blockerDetails = (Map<String, Object>) gaReadiness.get("blockerDetails");
    assertTrue(blockerDetails.containsKey("insufficient_recognition_signals"));
    assertTrue(blockerDetails.containsKey("insufficient_recognition_validators"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void recognitionGateClearsWhenSignalsReachThreshold() {
    prepareStableReadinessBaseline();
    seedRecognitionSignals(5, 5);
    recordHealthyWebVitalsPair();

    Map<String, Object> payload = requestWebVitals();
    @SuppressWarnings("unchecked")
    Map<String, Object> gaReadiness = (Map<String, Object>) payload.get("gaReadiness");

    assertEquals("ready", gaReadiness.get("status"));
    assertEquals(true, gaReadiness.get("recognitionGateEnabled"));
    assertEquals(5L, ((Number) gaReadiness.get("minRecognitionSignals")).longValue());
    assertEquals(3L, ((Number) gaReadiness.get("minRecognitionValidators")).longValue());
    assertEquals(70L, ((Number) gaReadiness.get("maxRecognitionValidatorSharePct")).longValue());
    assertTrue(((Number) gaReadiness.get("recognitionSignals")).longValue() >= 5L);
    assertTrue(((Number) gaReadiness.get("recognitionValidators")).longValue() >= 3L);
    assertTrue(((Number) gaReadiness.get("recognitionValidatorSharePct")).longValue() <= 70L);
    assertEquals("none", gaReadiness.get("primaryBlocker"));
    assertEquals("none", gaReadiness.get("primaryAction"));

    @SuppressWarnings("unchecked")
    List<String> blockers = (List<String>) gaReadiness.get("blockers");
    assertFalse(blockers.contains("insufficient_recognition_signals"));
    assertFalse(blockers.contains("insufficient_recognition_validators"));
    assertFalse(blockers.contains("high_recognition_validator_concentration"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void recognitionGateBlocksWhenValidatorsAreNotDiverse() {
    prepareStableReadinessBaseline();
    seedRecognitionSignals(5, 1);
    recordHealthyWebVitalsPair();

    Map<String, Object> payload = requestWebVitals();
    @SuppressWarnings("unchecked")
    Map<String, Object> gaReadiness = (Map<String, Object>) payload.get("gaReadiness");

    assertEquals("not_ready", gaReadiness.get("status"));
    assertEquals(true, gaReadiness.get("recognitionGateEnabled"));
    assertTrue(((Number) gaReadiness.get("recognitionSignals")).longValue() >= 5L);
    assertTrue(((Number) gaReadiness.get("recognitionValidators")).longValue() < 3L);
    assertEquals("insufficient_recognition_validators", gaReadiness.get("primaryBlocker"));
    assertEquals("expand_recognition_validator_pool", gaReadiness.get("primaryAction"));

    @SuppressWarnings("unchecked")
    List<String> blockers = (List<String>) gaReadiness.get("blockers");
    assertFalse(blockers.contains("insufficient_recognition_signals"));
    assertTrue(blockers.contains("insufficient_recognition_validators"));
    assertFalse(blockers.contains("high_recognition_validator_concentration"));
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void recognitionGateBlocksWhenValidatorConcentrationIsHigh() {
    prepareStableReadinessBaseline();
    seedRecognitionSignalsByValidators(
        "validator.user.0@example.com",
        "validator.user.0@example.com",
        "validator.user.0@example.com",
        "validator.user.0@example.com",
        "validator.user.0@example.com",
        "validator.user.0@example.com",
        "validator.user.0@example.com",
        "validator.user.0@example.com",
        "validator.user.1@example.com",
        "validator.user.2@example.com");
    recordHealthyWebVitalsPair();

    Map<String, Object> payload = requestWebVitals();
    @SuppressWarnings("unchecked")
    Map<String, Object> gaReadiness = (Map<String, Object>) payload.get("gaReadiness");

    assertEquals("not_ready", gaReadiness.get("status"));
    assertEquals(true, gaReadiness.get("recognitionGateEnabled"));
    assertTrue(((Number) gaReadiness.get("recognitionSignals")).longValue() >= 10L);
    assertTrue(((Number) gaReadiness.get("recognitionValidators")).longValue() >= 3L);
    assertTrue(((Number) gaReadiness.get("recognitionValidatorSharePct")).longValue() > 70L);
    assertEquals("high_recognition_validator_concentration", gaReadiness.get("primaryBlocker"));
    assertEquals("distribute_recognition_activity", gaReadiness.get("primaryAction"));

    @SuppressWarnings("unchecked")
    List<String> blockers = (List<String>) gaReadiness.get("blockers");
    assertFalse(blockers.contains("insufficient_recognition_signals"));
    assertFalse(blockers.contains("insufficient_recognition_validators"));
    assertTrue(blockers.contains("high_recognition_validator_concentration"));
  }

  private Map<String, Object> prepareStableReadinessBaseline() {
    seedLiveTraffic(12, 12);
    seedActivityLoopSignals(6, 6, 6);

    for (int i = 0; i < 22; i++) {
      recordHealthyWebVitalsPair();
    }
    requestWebVitals();

    Map<String, Object> payload = null;
    for (int i = 0; i < 3; i++) {
      recordHealthyWebVitalsPair();
      payload = requestWebVitals();
    }
    return payload;
  }

  private Map<String, Object> requestWebVitals() {
    return given()
        .when()
        .get("/api/private/admin/reputation/web-vitals")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getMap("$");
  }

  private void seedRecognitionSignals(int count, int validatorPoolSize) {
    int safePoolSize = Math.max(1, validatorPoolSize);
    for (int i = 0; i < count; i++) {
      String validator = "validator.user." + (i % safePoolSize) + "@example.com";
      boolean tracked =
          reputationEngineService.trackRecognition(
              "content_recommended",
              "target.user." + i + "@example.com",
              "community_content",
              "recognition-source-" + i,
              validator,
              "recommended");
      assertTrue(tracked);
    }
  }

  private void seedRecognitionSignalsByValidators(String... validators) {
    if (validators == null || validators.length == 0) {
      return;
    }
    for (int i = 0; i < validators.length; i++) {
      boolean tracked =
          reputationEngineService.trackRecognition(
              "content_recommended",
              "target.user.custom." + i + "@example.com",
              "community_content",
              "recognition-custom-source-" + i,
              validators[i],
              "recommended");
      assertTrue(tracked);
    }
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

  private void recordHealthyWebVitalsPair() {
    usageMetricsService.recordFunnelStep("reputation.hub.webvitals.sample");
    usageMetricsService.recordFunnelStep("reputation.hub.webvitals.lcp.good");
    usageMetricsService.recordFunnelStep("reputation.hub.webvitals.inp.good");
    usageMetricsService.recordFunnelStep("reputation.how.webvitals.sample");
    usageMetricsService.recordFunnelStep("reputation.how.webvitals.lcp.good");
    usageMetricsService.recordFunnelStep("reputation.how.webvitals.inp.good");
  }
}
