package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HealthResourceConsistencyTest {

  @Test
  void healthRootReturnsOk() {
    given()
        .when()
        .get("/health")
        .then()
        .statusCode(200)
        .body(containsString("ok"));
  }

  @Test
  void healthReadyReturnsReady() {
    given()
        .when()
        .get("/health/ready")
        .then()
        .statusCode(200)
        .body(containsString("ready"));
  }

  @Test
  void unknownRouteRemainsNotFound() {
    given()
        .when()
        .get("/__route_that_does_not_exist__")
        .then()
        .statusCode(404);
  }
}
