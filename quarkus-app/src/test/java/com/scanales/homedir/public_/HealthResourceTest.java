package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HealthResourceTest {

  @Test
  void healthRootReturnsAggregatedLiveness() {
    given()
        .when()
        .get("/health")
        .then()
        .statusCode(anyOf(is(200), is(503)))
        .body("status", anyOf(is("UP"), is("DOWN")))
        .body("checks", not(empty()));
  }

  @Test
  void healthReadyReturnsAggregatedReadiness() {
    given()
        .when()
        .get("/health/ready")
        .then()
        .statusCode(anyOf(is(200), is(503)))
        .body("status", anyOf(is("UP"), is("DOWN")))
        .body("checks", not(empty()));
  }

  @Test
  void livenessCheckIncludesOidc() {
    given()
        .when()
        .get("/health")
        .then()
        .body("checks.name", hasItem("oidc-provider"));
  }

  @Test
  void readinessCheckIncludesPersistence() {
    given()
        .when()
        .get("/health/ready")
        .then()
        .body("checks.name", hasItem("persistence"));
  }

  @Test
  void readinessCheckIncludesDiskSpace() {
    given()
        .when()
        .get("/health/ready")
        .then()
        .body("checks.name", hasItem("disk-space"));
  }

  @Test
  void readinessCheckIncludesExternalApis() {
    given()
        .when()
        .get("/health/ready")
        .then()
        .body("checks.name", hasItem("external-apis"));
  }
}
