package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SmallRyeHealthTest {

  @Test
  void qHealthReturnsAggregatedStatus() {
    given()
        .when()
        .get("/q/health")
        .then()
        .statusCode(anyOf(is(200), is(503)))
        .body("status", anyOf(is("UP"), is("DOWN")));
  }

  @Test
  void qHealthLiveReturnsLivenessChecks() {
    given()
        .when()
        .get("/q/health/live")
        .then()
        .statusCode(anyOf(is(200), is(503)))
        .body("status", anyOf(is("UP"), is("DOWN")))
        .body("checks.name", hasItem("oidc-provider"));
  }

  @Test
  void qHealthReadyReturnsReadinessChecks() {
    given()
        .when()
        .get("/q/health/ready")
        .then()
        .statusCode(anyOf(is(200), is(503)))
        .body("status", anyOf(is("UP"), is("DOWN")))
        .body("checks.name", hasItems("persistence", "disk-space", "external-apis"));
  }
}
