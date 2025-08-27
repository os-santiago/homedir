package com.scanales.eventflow.notifications;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import com.scanales.eventflow.notifications.Notification;
import com.scanales.eventflow.notifications.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NotificationPollingResourceTest {

  @Inject NotificationService service;
  @Inject NotificationConfig config;

  @BeforeEach
  void setup() {
    service.reset();
    config.pollLimit = 20;
    Notification n1 = new Notification();
    n1.userId = "u1";
    n1.talkId = "t1";
    n1.type = NotificationType.STARTED;
    n1.title = "n1";
    service.enqueue(n1);
    Notification n2 = new Notification();
    n2.userId = "u1";
    n2.talkId = "t2";
    n2.type = NotificationType.STARTED;
    n2.title = "n2";
    service.enqueue(n2);
  }

  @Test
  @TestSecurity(user = "u1")
  void sinceAndLimit() {
    long since = 0L;
    given()
        .queryParam("since", since)
        .queryParam("limit", 1)
        .when()
        .get("/api/notifications/next")
        .then()
        .statusCode(200)
        .body("items.size()", is(1))
        .header("Cache-Control", containsString("no-store"))
        .header("X-User-Scoped", is("true"));
  }
}
