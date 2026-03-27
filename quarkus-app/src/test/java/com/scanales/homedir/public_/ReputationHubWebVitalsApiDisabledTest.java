package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ReputationHubWebVitalsApiDisabledTest.Profile.class)
class ReputationHubWebVitalsApiDisabledTest {

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "reputation.engine.enabled", "false",
          "reputation.hub.ui.enabled", "false");
    }
  }

  @Test
  void returnsNotFoundWhenReputationHubIsDisabled() {
    given()
        .contentType("application/json")
        .body(Map.of("route", "hub", "lcp_ms", 2100))
        .when()
        .post("/api/community/reputation/web-vitals")
        .then()
        .statusCode(404);
  }
}
