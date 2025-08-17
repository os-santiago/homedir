package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class LoginPageTest {

  @Test
  public void loginAccessibleWithoutAuth() {
    given()
        .redirects()
        .follow(false)
        .accept("text/html")
        .when()
        .get("/login")
        .then()
        .statusCode(200)
        .body(containsString("Login"));
  }
}
