package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdminInsightsPageTest {

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanOpenInsightsPage() {
    given()
        .when()
        .get("/private/admin/insights")
        .then()
        .statusCode(200)
        .body(containsString("id=\"insightsRefreshBtn\""))
        .body(containsString("id=\"insightsInitiativesSearch\""))
        .body(containsString("id=\"insightsInitiativesState\""))
        .body(containsString("id=\"insightsInitiativesSort\""))
        .body(containsString("id=\"insightsInitiativesWindow\""))
        .body(containsString("id=\"insightsLoadMoreBtn\""));
  }
}
