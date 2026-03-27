package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scanales.homedir.TestDataDir;
import com.scanales.homedir.service.UsageMetricsService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
@TestProfile(ReputationHubWebVitalsApiResourceTest.Profile.class)
class ReputationHubWebVitalsApiResourceTest {

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "metrics.buffer.max-size", "200",
          "reputation.engine.enabled", "true",
          "reputation.hub.ui.enabled", "true");
    }
  }

  @Inject UsageMetricsService usageMetricsService;

  @BeforeEach
  void setUp() {
    usageMetricsService.reset();
  }

  @Test
  void tracksHubWebVitalsBuckets() {
    Map<String, Object> payload =
        Map.of("route", "hub", "lcp_ms", 3650, "inp_ms", 280, "viewport_width", 390);

    given()
        .contentType("application/json")
        .body(payload)
        .when()
        .post("/api/community/reputation/web-vitals")
        .then()
        .statusCode(202);

    Map<String, Long> snapshot = usageMetricsService.snapshot();
    assertEquals(1L, snapshot.getOrDefault("funnel:reputation.hub.webvitals.sample", 0L));
    assertEquals(1L, snapshot.getOrDefault("funnel:reputation.hub.webvitals.device.mobile", 0L));
    assertEquals(
        1L, snapshot.getOrDefault("funnel:reputation.hub.webvitals.lcp.needs_improvement", 0L));
    assertEquals(
        1L, snapshot.getOrDefault("funnel:reputation.hub.webvitals.inp.needs_improvement", 0L));
  }

  @Test
  void rejectsUnsupportedRoute() {
    given()
        .contentType("application/json")
        .body(Map.of("route", "board", "lcp_ms", 2500))
        .when()
        .post("/api/community/reputation/web-vitals")
        .then()
        .statusCode(400);
  }

  @Test
  void rejectsPayloadWithoutMetrics() {
    given()
        .contentType("application/json")
        .body(Map.of("route", "how"))
        .when()
        .post("/api/community/reputation/web-vitals")
        .then()
        .statusCode(400);
  }
}
