package com.scanales.eventflow.volunteers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class VolunteerLoungeApiResourceTest {

  private static final String EVENT_ID = "volunteer-lounge-event-1";

  @Inject VolunteerApplicationService volunteerApplicationService;
  @Inject VolunteerLoungeService volunteerLoungeService;
  @Inject VolunteerEventConfigService volunteerEventConfigService;
  @Inject EventService eventService;

  @BeforeEach
  void setup() {
    volunteerApplicationService.clearAllForTests();
    volunteerLoungeService.clearAllForTests();
    volunteerEventConfigService.resetForTests();
    eventService.reset();
    eventService.saveEvent(new Event(EVENT_ID, "Volunteer Lounge Event", "desc"));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void selectedVolunteerCanPostAndListMessages() {
    VolunteerApplication created =
        volunteerApplicationService.create(
            "member@example.com",
            "Member",
            new VolunteerApplicationService.CreateRequest(EVENT_ID, "About", "Reason", "Diff"));
    volunteerApplicationService.updateStatus(
        created.id(),
        VolunteerApplicationStatus.SELECTED,
        "admin@example.org",
        "selected",
        created.updatedAt());

    given()
        .contentType("application/json")
        .body(
            """
            {
              "body":"Setup complete for registration desk."
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/volunteers/lounge")
        .then()
        .statusCode(201)
        .body("item.id", notNullValue())
        .body("item.event_id", equalTo(EVENT_ID))
        .body("item.body", equalTo("Setup complete for registration desk."));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/volunteers/lounge?limit=20&offset=0")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("items", hasSize(1))
        .body("items[0].body", equalTo("Setup complete for registration desk."));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void nonSelectedVolunteerCannotAccessLounge() {
    volunteerApplicationService.create(
        "member@example.com",
        "Member",
        new VolunteerApplicationService.CreateRequest(EVENT_ID, "About", "Reason", "Diff"));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/volunteers/lounge")
        .then()
        .statusCode(403)
        .body("error", equalTo("volunteer_access_denied"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanAccessLoungeWithoutSelection() {
    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/volunteers/lounge")
        .then()
        .statusCode(200)
        .body("total", equalTo(0))
        .body("items", hasSize(0));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void loungePostRateLimitReturnsTooManyRequests() {
    VolunteerApplication created =
        volunteerApplicationService.create(
            "member@example.com",
            "Member",
            new VolunteerApplicationService.CreateRequest(EVENT_ID, "About", "Reason", "Diff"));
    volunteerApplicationService.updateStatus(
        created.id(),
        VolunteerApplicationStatus.SELECTED,
        "admin@example.org",
        "selected",
        created.updatedAt());

    given()
        .contentType("application/json")
        .body("{\"body\":\"First coordination message\"}")
        .when()
        .post("/api/events/" + EVENT_ID + "/volunteers/lounge")
        .then()
        .statusCode(201);

    given()
        .contentType("application/json")
        .body("{\"body\":\"Second coordination message\"}")
        .when()
        .post("/api/events/" + EVENT_ID + "/volunteers/lounge")
        .then()
        .statusCode(429)
        .body("error", equalTo("rate_limit"));
  }
}
