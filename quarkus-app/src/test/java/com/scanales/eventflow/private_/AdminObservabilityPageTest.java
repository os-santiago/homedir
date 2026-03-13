package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdminObservabilityPageTest {

  @Test
  @TestSecurity(user = "sergio.canales.e@gmail.com")
  void adminPageUsesSafeDatasetKeysForDeliveryPulseLabels() {
    given()
        .when()
        .get("/private/admin/observability")
        .then()
        .statusCode(200)
        .body(containsString("data-prod-success-week"))
        .body(containsString("data-pr-validation-week"))
        .body(not(containsString("data-prod-success-7d")))
        .body(not(containsString("data-pr-validation-7d")));
  }
}
