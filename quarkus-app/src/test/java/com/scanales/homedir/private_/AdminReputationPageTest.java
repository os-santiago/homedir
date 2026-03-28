package com.scanales.homedir.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdminReputationPageTest {

  @Test
  void anonymousUserMustAuthenticate() {
    given().when().get("/private/admin/reputation").then().statusCode(401);
  }

  @Test
  @TestSecurity(user = "alice")
  void regularUserCannotAccessPage() {
    given().when().get("/private/admin/reputation").then().statusCode(403);
  }

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminPageRendersGaEvidenceSections() {
    given()
        .when()
        .get("/private/admin/reputation")
        .then()
        .statusCode(200)
        .body(containsString("Close the remaining evidence for Reputation Hub"))
        .body(containsString("id=\"reputationDecision\""))
        .body(containsString("id=\"reputationMeasurement\""))
        .body(containsString("id=\"reputationSummary\""))
        .body(containsString("id=\"reputationChecklist\""))
        .body(containsString("data-check-p95"))
        .body(containsString("data-measurement-status"))
        .body(containsString("data-rollout-stage"))
        .body(containsString("/api/private/admin/reputation/web-vitals"));
  }
}
