package com.scanales.eventflow.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "member@example.com")
public class LoginCallbackRedirectTest {

  @Test
  public void privateLoginCallbackRedirectsToSafeInternalPath() {
    given()
        .header("X-Forwarded-For", "198.51.100.20")
        .redirects()
        .follow(false)
        .when()
        .get("/private/login-callback?redirect=/event/devopsdays-santiago-2026/cfp")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/event/devopsdays-santiago-2026/cfp"));
  }

  @Test
  public void privateLoginCallbackRejectsExternalRedirect() {
    given()
        .header("X-Forwarded-For", "198.51.100.21")
        .redirects()
        .follow(false)
        .when()
        .get("/private/login-callback?redirect=https://evil.example/phish")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/"));
  }

  @Test
  public void oidcLoginCallbackRedirectsToSafeInternalPath() {
    given()
        .header("X-Forwarded-For", "198.51.100.22")
        .redirects()
        .follow(false)
        .when()
        .get("/login/callback?redirect=/event/devopsdays-santiago-2026/cfp")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/event/devopsdays-santiago-2026/cfp"));
  }

  @Test
  public void oidcLoginCallbackFallsBackToProfileWhenRedirectIsInvalid() {
    given()
        .header("X-Forwarded-For", "198.51.100.23")
        .redirects()
        .follow(false)
        .when()
        .get("/login/callback?redirect=//evil.example/phish")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/private/profile"));
  }
}
