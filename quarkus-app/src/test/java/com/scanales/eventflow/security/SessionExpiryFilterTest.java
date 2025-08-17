package com.scanales.eventflow.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Tests for session expiration handling. */
@QuarkusTest
public class SessionExpiryFilterTest {

  @Test
  public void htmlRedirectsToRootWhenUnauthorized() {
    given()
        .redirects()
        .follow(false)
        .accept("text/html")
        .when()
        .get("/private/admin")
        .then()
        .statusCode(302)
        .header("Location", equalTo("/"))
        .header("X-Redirected-By", equalTo("session-expired"));
  }

  @Test
  public void apiUnauthorizedHasSessionHeader() {
    given()
        .redirects()
        .follow(false)
        .accept("application/json")
        .when()
        .get("/private/admin/metrics/status")
        .then()
        .statusCode(401)
        .header("X-Session-Expired", equalTo("true"))
        .header(
            "WWW-Authenticate",
            equalTo(
                "Bearer realm=\"eventflow\", error=\"invalid_token\", error_description=\"expired\""));
  }
}

