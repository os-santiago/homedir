package com.scanales.eventflow.volunteers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.eventflow.insights.DevelopmentInsightsLedgerService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UsageMetricsService;
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
  @Inject UsageMetricsService usageMetricsService;
  @Inject DevelopmentInsightsLedgerService insightsLedger;

  @BeforeEach
  void setup() {
    volunteerApplicationService.clearAllForTests();
    volunteerLoungeService.clearAllForTests();
    volunteerEventConfigService.resetForTests();
    eventService.reset();
    usageMetricsService.reset();
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
    long insightsEventsBefore = insightsLedger.status().storedEvents();

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
        .body("item.message_type", equalTo("post"))
        .body("item.body", equalTo("Setup complete for registration desk."));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/volunteers/lounge?limit=20&offset=0")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("items", hasSize(1))
        .body("items[0].message_type", equalTo("post"))
        .body("items[0].body", equalTo("Setup complete for registration desk."));

    assertTrue(usageMetricsService.snapshot().getOrDefault("funnel:volunteer_lounge_post", 0L) >= 1L);
    assertTrue(usageMetricsService.snapshot().getOrDefault("funnel:volunteer.lounge.post", 0L) >= 1L);
    assertTrue(insightsLedger.status().storedEvents() > insightsEventsBefore);
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

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanCreateAndDeleteAnnouncements() {
    given()
        .contentType("application/json")
        .body("{\"body\":\"Volunteer check-in starts at 08:00 at main entrance.\"}")
        .when()
        .post("/api/events/" + EVENT_ID + "/volunteers/lounge/announcements")
        .then()
        .statusCode(201)
        .body("item.id", notNullValue())
        .body("item.message_type", equalTo("announcement"));

    String announcementId =
        given()
            .accept("application/json")
            .when()
            .get("/api/events/" + EVENT_ID + "/volunteers/lounge/announcements")
            .then()
            .statusCode(200)
            .body("total", equalTo(1))
            .body("items", hasSize(1))
            .extract()
            .path("items[0].id");

    given()
        .when()
        .delete("/api/events/" + EVENT_ID + "/volunteers/lounge/announcements/" + announcementId)
        .then()
        .statusCode(204);

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/volunteers/lounge/announcements")
        .then()
        .statusCode(200)
        .body("total", equalTo(0));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void nonAdminCannotCreateAnnouncement() {
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
        .body("{\"body\":\"Unauthorized announcement attempt.\"}")
        .when()
        .post("/api/events/" + EVENT_ID + "/volunteers/lounge/announcements")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));
  }
}
