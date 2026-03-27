package com.scanales.homedir.private_;

import static io.restassured.RestAssured.given;
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
  }
}
