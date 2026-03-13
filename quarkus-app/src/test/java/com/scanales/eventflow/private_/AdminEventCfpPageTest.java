package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.eventflow.cfp.CfpSubmissionService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
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
        .body(containsString("/api/events/"))
        .body(containsString("/cfp/submissions"))
        .body(containsString("id=\"cfpEventConfigSaveBtn\""))
        .body(containsString("id=\"cfpPrevPageBtn\""))
        .body(containsString("id=\"cfpNextPageBtn\""))
        .body(containsString("id=\"cfpStatsSummary\""))
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
        .body(containsString("const submissionId = \"" + submission.id() + "\""));
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
