package com.scanales.logout;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class LogoutResourceTest {

  @Test
  public void logoutRedirectsAndClearsCookie() {
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/logout")
        .then()
        .statusCode(303)
        .header("Location", equalTo("/"))
        .header(
            "Set-Cookie",
            equalTo("q_session=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=None"));
  }
}
