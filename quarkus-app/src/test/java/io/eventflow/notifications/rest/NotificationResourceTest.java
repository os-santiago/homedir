package io.eventflow.notifications.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class NotificationResourceTest {

  @Test
  public void legacyEndpointReturnsGone() {
    given()
        .when()
        .get("/api/notifications")
        .then()
        .statusCode(410)
        .body("error", is("notifications-now-global"))
        .body("hint", containsString("/ws/global-notifications"));
  }
}
