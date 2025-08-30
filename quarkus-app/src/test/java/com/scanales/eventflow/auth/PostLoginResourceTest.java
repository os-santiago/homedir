package com.scanales.eventflow.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class PostLoginResourceTest {

  @Test
  @TestSecurity(user = "user@example.com")
  public void postLoginRedirectsToProfile() {
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/auth/post-login")
        .then()
        .statusCode(303)
        .header("Location", containsString("/profile"));
  }

  @Test
  public void postLoginRedirectsToIngresarWhenAnonymous() {
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/auth/post-login")
        .then()
        .statusCode(303)
        .header("Location", containsString("/ingresar?retry=1"));
  }
}
