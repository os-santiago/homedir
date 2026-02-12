package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class AdminEventCfpPageTest {

  @Inject EventService eventService;

  private static final String EVENT_ID = "cfp-admin-page-event";

  @AfterEach
  void cleanup() {
    eventService.deleteEvent(EVENT_ID);
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanOpenModerationPage() {
    eventService.saveEvent(new Event(EVENT_ID, "CFP Admin Event", "desc"));

    given()
        .when()
        .get("/private/admin/events/" + EVENT_ID + "/cfp")
        .then()
        .statusCode(200)
        .body(containsString("Moderacion CFP"))
        .body(containsString("/api/events/"))
        .body(containsString("/cfp/submissions"))
        .body(containsString("Panel admin"));
  }

  @Test
  @TestSecurity(user = "alice@example.com")
  void nonAdminCannotOpenModerationPage() {
    eventService.saveEvent(new Event(EVENT_ID, "CFP Admin Event", "desc"));

    given().when().get("/private/admin/events/" + EVENT_ID + "/cfp").then().statusCode(403);
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void missingEventReturnsNotFound() {
    given().when().get("/private/admin/events/missing-cfp-event/cfp").then().statusCode(404);
  }
}

