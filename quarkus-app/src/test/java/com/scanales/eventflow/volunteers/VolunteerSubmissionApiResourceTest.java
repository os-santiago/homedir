package com.scanales.eventflow.volunteers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scanales.eventflow.eventops.EventOperationsService;
import com.scanales.eventflow.eventops.EventStaffRole;
import com.scanales.eventflow.insights.DevelopmentInsightsLedgerService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.notifications.NotificationService;
import com.scanales.eventflow.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class VolunteerSubmissionApiResourceTest {

  private static final String EVENT_ID = "volunteer-api-event-1";

  @Inject VolunteerApplicationService volunteerApplicationService;
  @Inject VolunteerEventConfigService volunteerEventConfigService;
  @Inject EventService eventService;
  @Inject NotificationService notificationService;
  @Inject DevelopmentInsightsLedgerService insightsLedger;
  @Inject EventOperationsService eventOperationsService;

  @BeforeEach
  void setup() {
    volunteerApplicationService.clearAllForTests();
    volunteerEventConfigService.resetForTests();
    eventOperationsService.clearAllForTests();
    eventService.reset();
    notificationService.reset();
    eventService.saveEvent(new Event(EVENT_ID, "Volunteer API Event", "desc"));
  }

  @Test
  void createRequiresAuthentication() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "about_me":"I help with operations.",
              "join_reason":"I want to support this event.",
              "differentiator":"I can coordinate volunteers."
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/volunteers/submissions")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void authenticatedUserCanCreateAndReadMine() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "about_me":"I can help with attendee logistics and registration.",
              "join_reason":"I want to contribute to the community event.",
              "differentiator":"I already supported two local meetups."
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/volunteers/submissions")
        .then()
        .statusCode(201)
        .body("item.status", equalTo("applied"))
        .body("item.event_id", equalTo(EVENT_ID));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/volunteers/submissions/mine")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("items", hasSize(1))
        .body("items[0].status", equalTo("applied"));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void duplicateApplyReturnsConflict() {
    volunteerApplicationService.create(
        "member@example.com",
        "Member",
        new VolunteerApplicationService.CreateRequest(
            EVENT_ID,
            "About me",
            "Join reason",
            "Differentiator"));

    given()
        .contentType("application/json")
        .body(
            """
            {
              "about_me":"Second try",
              "join_reason":"Second reason"
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/volunteers/submissions")
        .then()
        .statusCode(409)
        .body("error", equalTo("already_applied"));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void withdrawSetsWithdrawnStatus() {
    VolunteerApplication created =
        volunteerApplicationService.create(
            "member@example.com",
            "Member",
            new VolunteerApplicationService.CreateRequest(
                EVENT_ID,
                "About me",
                "Join reason",
                "Differentiator"));

    given()
        .when()
        .post("/api/events/" + EVENT_ID + "/volunteers/submissions/" + created.id() + "/withdraw")
        .then()
        .statusCode(200)
        .body("item.status", equalTo("withdrawn"));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void adminEndpointsRequireAdmin() {
    VolunteerApplication created =
        volunteerApplicationService.create(
            "member@example.com",
            "Member",
            new VolunteerApplicationService.CreateRequest(
                EVENT_ID,
                "About me",
                "Join reason",
                "Differentiator"));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/volunteers/submissions")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));

    given()
        .contentType("application/json")
        .body(
            """
            {
              "status":"selected"
            }
            """)
        .when()
        .put("/api/events/" + EVENT_ID + "/volunteers/submissions/" + created.id() + "/status")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/volunteers/submissions/stats")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanListAndModerate() {
    VolunteerApplication created =
        volunteerApplicationService.create(
            "member@example.com",
            "Member",
            new VolunteerApplicationService.CreateRequest(
                EVENT_ID,
                "About me",
                "Join reason",
                "Differentiator"));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/volunteers/submissions?status=all&sort=created&limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("items", hasSize(1));

    given()
        .contentType("application/json")
        .body(
            """
            {
              "status":"under_review",
              "note":"Looks like a good fit.",
              "expected_updated_at":"%s"
            }
            """.formatted(created.updatedAt().toString()))
        .when()
        .put("/api/events/" + EVENT_ID + "/volunteers/submissions/" + created.id() + "/status")
        .then()
        .statusCode(200)
        .body("item.status", equalTo("under_review"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanRateSubmission() {
    VolunteerApplication created =
        volunteerApplicationService.create(
            "member@example.com",
            "Member",
            new VolunteerApplicationService.CreateRequest(
                EVENT_ID,
                "About me",
                "Join reason",
                "Differentiator"));

    given()
        .contentType("application/json")
        .body(
            """
            {
              "profile":5,
              "motivation":4,
              "differentiator":4,
              "expected_updated_at":"%s"
            }
            """.formatted(created.updatedAt().toString()))
        .when()
        .put("/api/events/" + EVENT_ID + "/volunteers/submissions/" + created.id() + "/rating")
        .then()
        .statusCode(200)
        .body("item.rating_weighted", notNullValue())
        .body("item.rating_profile", equalTo(5));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanManageEventConfig() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "accepting_submissions":false,
              "opens_at":"2026-01-01T00:00:00Z",
              "closes_at":"2026-01-31T00:00:00Z"
            }
            """)
        .when()
        .put("/api/events/" + EVENT_ID + "/volunteers/submissions/event-config")
        .then()
        .statusCode(200)
        .body("has_override", equalTo(true))
        .body("resolved.accepting_submissions", equalTo(false));

    given()
        .when()
        .delete("/api/events/" + EVENT_ID + "/volunteers/submissions/event-config")
        .then()
        .statusCode(200)
        .body("cleared", equalTo(true));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void statsIncludesSelectedCount() {
    VolunteerApplication created =
        volunteerApplicationService.create(
            "member@example.com",
            "Member",
            new VolunteerApplicationService.CreateRequest(
                EVENT_ID,
                "About me",
                "Join reason",
                "Differentiator"));

    volunteerApplicationService.updateStatus(
        created.id(),
        VolunteerApplicationStatus.SELECTED,
        "admin@example.org",
        "selected",
        created.updatedAt());

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/volunteers/submissions/stats")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("selected", equalTo(1));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void createRejectedWhenWindowClosed() {
    volunteerEventConfigService.upsert(
        EVENT_ID,
        new VolunteerEventConfigService.UpdateRequest(
            false,
            Instant.now().minusSeconds(3600),
            Instant.now().plusSeconds(3600)));

    given()
        .contentType("application/json")
        .body(
            """
            {
              "about_me":"I can help.",
              "join_reason":"I want to support."
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/volunteers/submissions")
        .then()
        .statusCode(409)
        .body("error", equalTo("submissions_closed"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void statusUpdateNotifiesApplicantAndTracksMetricsAndInsights() {
    VolunteerApplication created =
        volunteerApplicationService.create(
            "member@example.com",
            "Member",
            new VolunteerApplicationService.CreateRequest(
                EVENT_ID,
                "About me",
                "Join reason",
                "Differentiator"));
    long eventsBefore = insightsLedger.status().storedEvents();

    given()
        .contentType("application/json")
        .body(
            """
            {
              "status":"selected",
              "note":"Welcome to the volunteer squad",
              "expected_updated_at":"%s"
            }
            """
                .formatted(created.updatedAt().toString()))
        .when()
        .put("/api/events/" + EVENT_ID + "/volunteers/submissions/" + created.id() + "/status")
        .then()
        .statusCode(200)
        .body("item.status", equalTo("selected"));

    var notifications = notificationService.listForUser("member@example.com", 10, false);
    assertEquals(1, notifications.size());
    assertEquals("Volunteer application update", notifications.get(0).title);
    assertTrue(notifications.get(0).message != null && notifications.get(0).message.contains("selected"));

    assertTrue(
        eventOperationsService
            .findStaffByEventAndUser(EVENT_ID, "member@example.com", EventStaffRole.VOLUNTEER)
            .map(item -> item.active() && "volunteer".equals(item.role()))
            .orElse(false));

    assertTrue(insightsLedger.status().storedEvents() > eventsBefore);
  }
}
