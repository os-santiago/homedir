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
        .when()
        .get("/private")
        .then()
        // Unauthorized access should be rejected with 401
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "alice")
  public void privateAuthorized() {
    given().when().get("/private").then().statusCode(200).body(containsString("alice"));
  }
}
