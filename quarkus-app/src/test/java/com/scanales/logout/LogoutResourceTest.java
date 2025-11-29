package com.scanales.logout;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

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
        .header("Location", "/")
        .header("Set-Cookie", containsString("q_session=; Path=/; Max-Age=0; HttpOnly; SameSite="))
        .header("Set-Cookie", not(containsString("Secure")));
  }
}
