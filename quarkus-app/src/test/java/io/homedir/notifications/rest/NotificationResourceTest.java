package io.homedir.notifications.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NotificationResourceTest {
  private static final String TEST_CLIENT_IP = "198.51.100.77";

  @Test
  void returnsGoneForAllOperations() {
    given()
        .header("X-Forwarded-For", TEST_CLIENT_IP)
        .when()
        .get("/api/notifications")
        .then()
        .statusCode(410)
        .body("error", equalTo("notifications-now-global"))
        .body("hint", containsString("global"));

    given()
        .header("X-Forwarded-For", TEST_CLIENT_IP)
        .when()
        .post("/api/notifications/anything")
        .then()
        .statusCode(410)
        .body("error", equalTo("notifications-now-global"));
  }
}
