package io.eventflow.notifications.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.eventflow.notifications.NotificationConfig;
import com.scanales.eventflow.notifications.NotificationService;
import com.scanales.eventflow.notifications.NotificationType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the notifications center page. */
@QuarkusTest
public class NotificationPageResourceTest {

  @Inject NotificationService service;
  @Inject NotificationConfig config;

  @BeforeEach
  void setup() {
    config.enabled = true;
    service.reset();
    // enqueue a sample notification for the authenticated user
    var n = new com.scanales.eventflow.notifications.Notification();
    n.userId = "u1";
    n.talkId = "t1";
    n.type = NotificationType.STARTED;
    n.title = "hello";
    service.enqueue(n);
  }

  @Test
  public void centerRequiresAuth() {
    given().when().get("/notifications/center").then().statusCode(401);
  }

  @Test
  @TestSecurity(user = "u1")
  public void centerRendersForUser() {
    given()
        .when()
        .get("/notifications/center")
        .then()
        .statusCode(200)
        .body(containsString("Notificaciones"));
  }
}
