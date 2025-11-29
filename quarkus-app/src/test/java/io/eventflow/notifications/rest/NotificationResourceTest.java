package io.eventflow.notifications.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NotificationResourceTest {

  @Test
  void returnsGoneForAllOperations() {
    given()
        .when()
        .get("/api/notifications")
        .then()
        .statusCode(410)
        .body("error", equalTo("notifications-now-global"))
        .body("hint", containsString("global"));

    given()
        .when()
        .post("/api/notifications/anything")
        .then()
        .statusCode(410)
        .body("error", equalTo("notifications-now-global"));
  }
}
