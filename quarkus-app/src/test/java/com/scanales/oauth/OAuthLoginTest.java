package com.scanales.oauth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class OAuthLoginTest {

  @Test
  public void privateUnauthorized() {
    given()
        .header("X-Forwarded-For", "198.51.100.10")
        .when()
        .get("/private")
        .then()
        // Unauthorized access should be rejected with 401
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "alice")
  public void privateAuthorized() {
    given()
        .header("X-Forwarded-For", "198.51.100.11")
        .when()
        .get("/private")
        .then()
        .statusCode(200)
        .body(containsString("alice"));
  }
}
