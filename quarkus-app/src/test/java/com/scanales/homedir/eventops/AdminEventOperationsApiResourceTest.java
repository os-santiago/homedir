package com.scanales.homedir.eventops;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.scanales.homedir.model.Event;
import com.scanales.homedir.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdminEventOperationsApiResourceTest {

  private static final String EVENT_ID = "event-ops-admin-event";

  @Inject EventService eventService;
  @Inject EventOperationsService eventOperationsService;

  @BeforeEach
  void setup() {
    eventOperationsService.clearAllForTests();
    eventService.reset();
    eventService.saveEvent(new Event(EVENT_ID, "Event Ops Admin Event", "desc"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanManageStaffSpacesShiftsAndActivities() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "user_name":"Volunteer One",
              "role":"volunteer",
              "source":"manual",
              "active":true
            }
            """)
        .when()
        .put("/api/private/admin/events/" + EVENT_ID + "/ops/staff/member@example.com")
        .then()
        .statusCode(200)
        .body("item.role", equalTo("volunteer"))
        .body("item.active", equalTo(true));

    String spaceId =
        given()
            .contentType("application/json")
            .body(
                """
                {
                  "name":"Main Stage",
                  "type":"main_stage",
                  "capacity":400,
                  "active":true
                }
                """)
            .when()
            .put("/api/private/admin/events/" + EVENT_ID + "/ops/spaces/main-stage")
            .then()
            .statusCode(200)
            .body("item.name", equalTo("Main Stage"))
            .extract()
            .path("item.id");

    given()
        .contentType("application/json")
        .body(
            """
            {
              "user_id":"member@example.com",
              "start_at":"2026-09-08T08:00:00Z",
              "end_at":"2026-09-08T12:00:00Z"
            }
            """)
        .when()
        .post("/api/private/admin/events/" + EVENT_ID + "/ops/spaces/" + spaceId + "/shifts")
        .then()
        .statusCode(201)
        .body("item.space_id", equalTo(spaceId))
        .body("item.user_id", equalTo("member@example.com"));

    given()
        .contentType("application/json")
        .body(
            """
            {
              "space_id":"%s",
              "title":"Volunteer briefing",
              "details":"Coordinator sync before doors open.",
              "visibility":"staff",
              "start_at":"2026-09-08T08:30:00Z",
              "end_at":"2026-09-08T09:00:00Z"
            }
            """
                .formatted(spaceId))
        .when()
        .put("/api/private/admin/events/" + EVENT_ID + "/ops/activities/briefing-1")
        .then()
        .statusCode(200)
        .body("item.title", equalTo("Volunteer briefing"))
        .body("item.visibility", equalTo("staff"));

    given()
        .accept("application/json")
        .when()
        .get("/api/private/admin/events/" + EVENT_ID + "/ops/runsheet?visibility=staff")
        .then()
        .statusCode(200)
        .body("staff", hasSize(1))
        .body("spaces", hasSize(1))
        .body("shifts", hasSize(1))
        .body("activities", hasSize(1));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void nonAdminCannotAccessAdminOpsEndpoints() {
    given()
        .accept("application/json")
        .when()
        .get("/api/private/admin/events/" + EVENT_ID + "/ops/staff")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_view_required"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void invalidVisibilityReturnsBadRequest() {
    given()
        .accept("application/json")
        .when()
        .get("/api/private/admin/events/" + EVENT_ID + "/ops/activities?visibility=invalid")
        .then()
        .statusCode(400)
        .body("error", equalTo("invalid_visibility"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void invalidShiftWindowIsRejected() {
    eventOperationsService.upsertStaff(
        EVENT_ID, "member@example.com", "Member", EventStaffRole.VOLUNTEER, "manual", true);
    EventSpace space =
        eventOperationsService.upsertSpace(
            EVENT_ID, "event-ops-admin-event:space:test", "Ops Desk", EventSpaceType.OTHER, 10, true);
    given()
        .contentType("application/json")
        .body(
            """
            {
              "user_id":"member@example.com",
              "start_at":"%s",
              "end_at":"%s"
            }
            """
                .formatted(Instant.parse("2026-09-08T12:00:00Z"), Instant.parse("2026-09-08T11:00:00Z")))
        .when()
        .post("/api/private/admin/events/" + EVENT_ID + "/ops/spaces/" + space.id() + "/shifts")
        .then()
        .statusCode(400)
        .body("error", equalTo("invalid_shift_window"));
  }
}
