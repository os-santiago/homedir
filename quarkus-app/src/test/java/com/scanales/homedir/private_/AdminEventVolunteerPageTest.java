package com.scanales.homedir.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.homedir.model.Event;
import com.scanales.homedir.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdminEventVolunteerPageTest {

  @Inject EventService eventService;

  private static final String EVENT_ID = "volunteer-admin-page-event";

  @AfterEach
  void cleanup() {
    eventService.deleteEvent(EVENT_ID);
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanOpenVolunteerModerationPage() {
    eventService.saveEvent(new Event(EVENT_ID, "Volunteer Admin Event", "desc"));

    given()
        .when()
        .get("/private/admin/events/" + EVENT_ID + "/volunteers")
        .then()
        .statusCode(200)
        .body(containsString("/api/events/"))
        .body(containsString("/volunteers/submissions"))
        .body(containsString("id=\"volConfigSaveBtn\""))
        .body(containsString("id=\"volPrevPageBtn\""))
        .body(containsString("id=\"volNextPageBtn\""))
        .body(containsString("id=\"volStatsSummary\""));
  }

  @Test
  @TestSecurity(user = "alice@example.com")
  void nonAdminCannotOpenVolunteerModerationPage() {
    eventService.saveEvent(new Event(EVENT_ID, "Volunteer Admin Event", "desc"));

    given().when().get("/private/admin/events/" + EVENT_ID + "/volunteers").then().statusCode(403);
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void missingEventReturnsNotFound() {
    given().when().get("/private/admin/events/missing-volunteer-event/volunteers").then().statusCode(404);
  }
}
