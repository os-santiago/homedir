package com.scanales.homedir.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.homedir.cfp.CfpSubmissionService;
import com.scanales.homedir.eventops.EventOperationsService;
import com.scanales.homedir.eventops.EventStaffRole;
import com.scanales.homedir.model.Event;
import com.scanales.homedir.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class AdminEventCfpPageTest {

  @Inject EventService eventService;

  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject EventOperationsService eventOperationsService;

  private static final String EVENT_ID = "cfp-admin-page-event";

  @AfterEach
  void cleanup() {
    eventService.deleteEvent(EVENT_ID);
    cfpSubmissionService.clearAllForTests();
    eventOperationsService.clearAllForTests();
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
        .body(containsString("/api/events/"))
        .body(containsString("/cfp/submissions"))
        .body(containsString("id=\"cfpEventConfigSaveBtn\""))
        .body(containsString("id=\"cfpFilterProposedBy\""))
        .body(containsString("id=\"cfpFilterTitle\""))
        .body(containsString("id=\"cfpFilterTrack\""))
        .body(containsString("id=\"cfpPrevPageBtn\""))
        .body(containsString("id=\"cfpNextPageBtn\""))
        .body(containsString("id=\"cfpStatsSummary\""))
        .body(containsString("id=\"cfpApplyFiltersBtn\""))
        .body(containsString("id=\"cfpClearFiltersBtn\""))
        .body(containsString("data-cfp-admin-nav=\"cfp-overview-panel\""))
        .body(containsString("data-cfp-admin-nav=\"cfp-configuration-panel\""))
        .body(containsString("data-cfp-admin-nav=\"cfp-review-panel\""))
        .body(containsString("Open detail"))
        .body(containsString("/cfp/submissions/"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanOpenSubmissionDetailPage() {
    eventService.saveEvent(new Event(EVENT_ID, "CFP Admin Event", "desc"));
    var submission =
        cfpSubmissionService.create(
            "speaker@example.org",
            "Speaker",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Observability as product signal",
                "Short summary",
                "Longer proposal detail for the admin page.",
                "advanced",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of("https://example.org/talk")));

    given()
        .when()
        .get("/private/admin/events/" + EVENT_ID + "/cfp/submissions/" + submission.id())
        .then()
        .statusCode(200)
        .body(containsString("id=\"cfpDetailShell\""))
        .body(containsString("id=\"cfpDetailFeedback\""))
        .body(containsString("id=\"cfpActionModal\""))
        .body(containsString("id=\"cfpBackToOverviewLink\""))
        .body(containsString("Add a moderation note before rejecting this proposal."))
        .body(containsString("Update confirmed"))
        .body(containsString("const submissionId = \"" + submission.id() + "\""));
  }

  @Test
  @TestSecurity(user = "alice@example.com")
  void nonAdminCannotOpenModerationPage() {
    eventService.saveEvent(new Event(EVENT_ID, "CFP Admin Event", "desc"));

    given().when().get("/private/admin/events/" + EVENT_ID + "/cfp").then().statusCode(403);
  }

  @Test
  @TestSecurity(user = "reviewer@example.org")
  void reviewerCanOpenModerationPageInReadOnlyMode() {
    eventService.saveEvent(new Event(EVENT_ID, "CFP Admin Event", "desc"));
    eventOperationsService.upsertStaff(
        EVENT_ID,
        "reviewer@example.org",
        "Reviewer",
        EventStaffRole.CFP_REVIEWER,
        "manual",
        true);

    given()
        .when()
        .get("/private/admin/events/" + EVENT_ID + "/cfp")
        .then()
        .statusCode(200)
        .body(containsString("/api/events/"))
        .body(containsString("/cfp/submissions"))
        .body(containsString("data-cfp-admin-nav=\"cfp-overview-panel\""))
        .body(containsString("data-cfp-admin-nav=\"cfp-review-panel\""))
        .body(containsString("const canManage = false"));
  }

  @Test
  @TestSecurity(user = "reviewer@example.org")
  void reviewerCanOpenSubmissionDetailPageInReadOnlyMode() {
    eventService.saveEvent(new Event(EVENT_ID, "CFP Admin Event", "desc"));
    eventOperationsService.upsertStaff(
        EVENT_ID,
        "reviewer@example.org",
        "Reviewer",
        EventStaffRole.CFP_REVIEWER,
        "manual",
        true);
    var submission =
        cfpSubmissionService.create(
            "speaker@example.org",
            "Speaker",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Observability as product signal",
                "Short summary",
                "Longer proposal detail for the admin page.",
                "advanced",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of("https://example.org/talk")));

    given()
        .when()
        .get("/private/admin/events/" + EVENT_ID + "/cfp/submissions/" + submission.id())
        .then()
        .statusCode(200)
        .body(containsString("id=\"cfpDetailShell\""))
        .body(containsString("const submissionId = \"" + submission.id() + "\""))
        .body(containsString("const canManage = false"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void missingEventReturnsNotFound() {
    given().when().get("/private/admin/events/missing-cfp-event/cfp").then().statusCode(404);
  }
}
