package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.scanales.eventflow.notifications.NotificationService;
import com.scanales.eventflow.notifications.NotificationConfig;
import com.scanales.eventflow.service.UserScheduleService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ProfileResourceTest {

  @Inject UserScheduleService userSchedule;

  @Inject NotificationService notifications;
  @Inject NotificationConfig config;

  @BeforeEach
  void setup() {
    config.enabled = true;
    config.userCap = 100;
    config.globalCap = 1000;
    config.maxQueueSize = 10000;
    config.dedupeWindow = java.time.Duration.ofMinutes(30);
    notifications.reset();
    userSchedule.reset();
  }

  @Test
  @TestSecurity(user = "user@example.com")
  public void addTalkJson() {
    given()
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .body("{}")
        .when()
        .post("/private/profile/add/t1")
        .then()
        .statusCode(200)
        .body("status", is("added"))
        .body("talkId", is("t1"));
  }

  @Test
  @TestSecurity(user = "user@example.com")
  public void removeTalkJson() {
    // ensure talk exists
    given()
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .body("{}")
        .post("/private/profile/add/t2")
        .then()
        .statusCode(200);

    given()
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .body("{}")
        .when()
        .post("/private/profile/remove/t2")
        .then()
        .statusCode(200)
        .body("status", is("removed"))
        .body("talkId", is("t2"));
  }

  @Test
  @TestSecurity(user = "user@example.com")
  public void addTalkHtmlRedirect() {
    given()
        .redirects()
        .follow(false)
        .header("Accept", "text/html")
        .header("Content-Type", "application/json")
        .body("{}")
        .when()
        .post("/private/profile/add/t3")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/talk/t3"));
  }

  @Test
  @TestSecurity(user = "user@example.com")
  public void removeTalkHtmlRedirect() {
    // ensure talk exists
    given()
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .body("{}")
        .post("/private/profile/add/t4")
        .then()
        .statusCode(200);

    given()
        .redirects()
        .follow(false)
        .header("Accept", "text/html")
        .header("Content-Type", "application/json")
        .body("{}")
        .when()
        .post("/private/profile/remove/t4")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/private/profile"));
  }

  @Test
  @TestSecurity(user = "user@example.com")
  public void testNotificationEndpointCreatesNotification() {
    given()
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .body("{\"talkId\":\"t1\"}")
        .when()
        .post("/private/profile/test-notification")
        .then()
        .statusCode(200)
        .body("status", is("ok"));
    assertEquals(1, notifications.listForUser("user@example.com", 10, false).size());
  }

  @Test
  @TestSecurity(user = "user@example.com")
  public void updateTalkRejectsUnknownProperty() {
    given()
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .body("{\"unknown\":true}")
        .when()
        .post("/private/profile/update/t5")
        .then()
        .statusCode(400);
  }

  @Test
  @TestSecurity(user = "user@example.com")
  public void updateTalkInvalidRating() {
    given()
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .body("{\"rating\":6}")
        .when()
        .post("/private/profile/update/t6")
        .then()
        .statusCode(400);
  }

  @Test
  @TestSecurity(user = "user@example.com")
  public void visitedParamAddsTalkAndMarksAttended() {
    assertFalse(userSchedule.getTalkDetailsForUser("user@example.com").containsKey("t7"));
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/private/profile/add/t7?visited=true")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/private/profile"));
    var details = userSchedule.getTalkDetailsForUser("user@example.com").get("t7");
    assertNotNull(details);
    assertTrue(details.attended);
  }

  @Test
  @TestSecurity(user = "user@example.com")
  public void attendedParamAddsTalkAndMarksAttended() {
    assertFalse(userSchedule.getTalkDetailsForUser("user@example.com").containsKey("t8"));
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/private/profile/add/t8?attended=true")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/private/profile"));
    var details = userSchedule.getTalkDetailsForUser("user@example.com").get("t8");
    assertNotNull(details);
    assertTrue(details.attended);
  }
}
