package com.scanales.homedir.community;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CommunityModerationPageTest {

  @Test
  void moderationPageRendersForAnonymous() {
    given()
        .redirects()
        .follow(false)
        .header("Accept-Language", "en")
        .when()
        .get("/comunidad/moderation")
        .then()
        .statusCode(303)
        .header("Location", containsString("/comunidad/propose"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void moderationQueueVisibleForAdmin() {
    given()
        .redirects()
        .follow(false)
        .header("Accept-Language", "en")
        .when()
        .get("/comunidad/moderation")
        .then()
        .statusCode(303)
        .header("Location", containsString("/comunidad/propose"));
  }
}
