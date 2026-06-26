package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HealthResourceConsistencyTest {

  @Test
  void healthRootHasStatusAndChecks() {
    given()
        .when()
        .get("/health")
        .then()
        .statusCode(anyOf(is(200), is(503)))
        .body("$", hasKey("status"))
        .body("$", hasKey("checks"));
  }

  @Test
  void healthReadyHasStatusAndChecks() {
    given()
        .when()
        .get("/health/ready")
        .then()
        .statusCode(anyOf(is(200), is(503)))
        .body("$", hasKey("status"))
        .body("$", hasKey("checks"));
  }

  @Test
  void unknownRouteRemainsNotFound() {
    given().when().get("/__route_that_does_not_exist__").then().statusCode(404);
  }
}
