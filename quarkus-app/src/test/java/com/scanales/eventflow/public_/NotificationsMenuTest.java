package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class NotificationsMenuTest {

  @Test
  public void menuVisibleForAnonymous() {
    given()
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("/notifications/center"));
  }

  @Test
  @TestSecurity(user = "user@example.com")
  public void menuVisibleForAuthenticated() {
    given()
        .accept("text/html")
        .when()
        .get("/")
        .then()
        .statusCode(200)
        .body(containsString("/notifications/center"));
  }
}
