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
        .get("/ingresar")
        .then()
        .statusCode(200)
        .body(containsString("Ingresar"));
  }

  @Test
  @io.quarkus.test.security.TestSecurity(user = "user@example.com")
  public void ingresarRedirectsWhenAuthenticated() {
    given()
        .redirects()
        .follow(false)
        .accept("text/html")
        .when()
        .get("/ingresar")
        .then()
        .statusCode(303)
        .header("Location", containsString("/profile"));
  }
}
