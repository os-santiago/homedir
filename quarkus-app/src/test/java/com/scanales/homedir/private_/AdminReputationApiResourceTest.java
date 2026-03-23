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
}
