package com.scanales.logout;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class LogoutResourceTest {

  @Test
  public void logoutRedirectsAndClearsCookie() {
    var response = given()
        .redirects()
        .follow(false)
        .when()
        .get("/logout")
        .then()
        .statusCode(303)
        .header("Location", "/")
        .extract()
        .response();

    // Verify that at least one Set-Cookie header clears the q_session cookie
    var cookies = response.getHeaders().getValues("Set-Cookie");
    var hasQSessionClear = cookies.stream().anyMatch(c -> c.contains("q_session=") && c.contains("Max-Age=0"));
    var hasSecure = cookies.stream().anyMatch(c -> c.contains("Secure"));

    org.junit.jupiter.api.Assertions.assertTrue(
        hasQSessionClear, "Expected q_session cookie to be cleared");
    org.junit.jupiter.api.Assertions.assertFalse(
        hasSecure, "Expected cookies to not have Secure flag");
  }
}
