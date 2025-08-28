package io.eventflow.notifications.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

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
    config.sseEnabled = true;
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
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/notifications/center")
        .then()
        .statusCode(anyOf(is(302), is(303)));
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

  @Test
  public void apiRequiresAuth() {
    given().when().get("/api/notifications?limit=5").then().statusCode(401);
  }

  @Test
  public void streamRequiresAuth() {
    given().when().get("/api/notifications/stream").then().statusCode(401);
  }

}
