package com.scanales.eventflow.community;

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
        .when()
        .get("/comunidad/moderation")
        .then()
        .statusCode(200)
        .body(containsString("Proponer contenido"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void moderationQueueVisibleForAdmin() {
    given()
        .when()
        .get("/comunidad/moderation")
        .then()
        .statusCode(200)
        .body(containsString("Moderation queue"));
  }
}
