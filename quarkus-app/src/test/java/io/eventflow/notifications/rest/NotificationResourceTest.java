package io.eventflow.notifications.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import com.scanales.eventflow.notifications.Notification;
import com.scanales.eventflow.notifications.NotificationConfig;
import com.scanales.eventflow.notifications.NotificationService;
import com.scanales.eventflow.notifications.NotificationType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class NotificationResourceTest {

  @Inject NotificationService service;
  @Inject NotificationConfig config;

  @BeforeEach
  void setup() {
    config.enabled = true;
    config.maxQueueSize = 10000;
    config.dropOnQueueFull = false;
    config.userCap = 100;
    config.globalCap = 1000;
    config.dedupeWindow = Duration.ofMinutes(30);
    service.reset();
    Notification n1 = new Notification();
    n1.userId = "u1";
    n1.talkId = "t1";
    n1.type = NotificationType.STARTED;
    n1.title = "n1";
    service.enqueue(n1);
    Notification n2 = new Notification();
    n2.userId = "u2";
    n2.talkId = "t1";
    n2.type = NotificationType.STARTED;
    n2.title = "n2";
    service.enqueue(n2);
  }

  @Test
  @TestSecurity(user = "u1")
  public void listReturnsOnlyOwnNotifications() {
    given()
        .when()
        .get("/api/notifications")
        .then()
        .statusCode(200)
        .body("items.size()", is(1))
        .body("unreadCount", is(1))
        .header("Cache-Control", containsString("no-store"))
        .header("X-User-Scoped", is("true"));
  }

  @Test
  @TestSecurity(user = "u1")
  public void cannotDeleteOthersNotification() {
    String foreignId = service.listForUser("u2", 10, false).get(0).id;
    given()
        .when()
        .delete("/api/notifications/" + foreignId)
        .then()
        .statusCode(404);
  }

  @Test
  public void unauthorizedRequestsReturn401() {
    given()
        .when()
        .get("/api/notifications")
        .then()
        .statusCode(401)
        .header("X-Session-Expired", is("true"));
  }
}
