package com.scanales.eventflow.cfp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.SpeakerService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CfpSubmissionApiResourceTest {

  private static final String EVENT_ID = "cfp-api-event-1";

  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject EventService eventService;
  @Inject SpeakerService speakerService;

  @BeforeEach
  void setup() {
    cfpSubmissionService.clearAllForTests();
    speakerService.reset();
    eventService.reset();
    eventService.saveEvent(new Event(EVENT_ID, "CFP API Event", "desc"));
  }

  @Test
  void createRequiresAuthentication() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"Observability for Platform Teams",
              "summary":"Summary",
              "abstract_text":"Long abstract."
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void authenticatedUserCanCreateAndListMine() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"Cloud Native Platform Stories",
              "summary":"Real-world platform stories.",
              "abstract_text":"Detailed abstract for platform stories.",
              "level":"intermediate",
              "format":"talk",
              "duration_min":45,
              "language":"en",
              "track":"platform-engineering-idp",
              "links":["https://example.org/talk"]
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions")
        .then()
        .statusCode(201)
        .body("item.status", equalTo("pending"))
        .body("item.event_id", equalTo(EVENT_ID))
        .body("item.track", equalTo("platform-engineering-idp"));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/mine?limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].title", equalTo("Cloud Native Platform Stories"))
        .body("items[0].track", equalTo("platform-engineering-idp"));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void createRejectsInvalidControlledValues() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"Invalid controlled values",
              "summary":"Summary",
              "abstract_text":"Abstract",
              "level":"any-random-level",
              "format":"talk",
              "duration_min":45,
              "language":"en",
              "track":"platform-engineering-idp"
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions")
        .then()
        .statusCode(400)
        .body("error", equalTo("invalid_level"));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void createFailsWhenEventDoesNotExist() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"Talk",
              "summary":"Summary",
              "abstract_text":"Abstract",
              "level":"beginner",
              "format":"talk",
              "duration_min":30,
              "language":"en",
              "track":"platform-engineering-idp"
            }
            """)
        .when()
        .post("/api/events/missing-event/cfp/submissions")
        .then()
        .statusCode(404)
        .body("error", equalTo("event_not_found"));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void moderationEndpointsRequireAdmin() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Talk title",
                "Summary",
                "Long abstract",
                "beginner",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of("devops"),
                List.of()));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions?limit=10&offset=0")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));

    given()
        .contentType("application/json")
        .body("{\"status\":\"accepted\",\"note\":\"ok\"}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/status")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));

    given()
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/promote")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanListPendingAndAcceptSubmission() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "AI for Platform Engineering",
                "Summary",
                "Long abstract",
                "advanced",
                "talk",
                45,
                "en",
                "ai-agents-copilots",
                List.of("ai", "platform"),
                List.of("https://example.org/session")));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions?limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].id", equalTo(created.id()))
        .body("items[0].status", equalTo("pending"));

    given()
        .contentType("application/json")
        .body("{\"status\":\"under_review\",\"note\":\"triage\"}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/status")
        .then()
        .statusCode(200)
        .body("item.status", equalTo("under_review"));

    given()
        .contentType("application/json")
        .body("{\"status\":\"accepted\",\"note\":\"great fit\"}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/status")
        .then()
        .statusCode(200)
        .body("item.status", equalTo("accepted"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanPromoteAcceptedSubmissionToCatalog() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member Name",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Kubernetes Day-2 Lessons",
                "Practical lessons",
                "Deep dive abstract",
                "intermediate",
                "talk",
                45,
                "en",
                "cloud-native-security",
                List.of("kubernetes"),
                List.of()));

    cfpSubmissionService.updateStatus(created.id(), CfpSubmissionStatus.ACCEPTED, "admin@example.org", "ok");

    String speakerId =
        given()
            .when()
            .post("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/promote")
            .then()
            .statusCode(200)
            .body("created_speaker", equalTo(true))
            .body("created_talk", equalTo(true))
            .extract()
            .path("speaker_id");

    String talkId =
        given()
            .when()
            .post("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/promote")
            .then()
            .statusCode(200)
            .body("created_speaker", equalTo(false))
            .body("created_talk", equalTo(false))
            .extract()
            .path("talk_id");

    assertNotNull(speakerService.getSpeaker(speakerId));
    assertNotNull(speakerService.getTalk(speakerId, talkId));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void promoteRequiresAcceptedStatus() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Async persistence deep dive",
                "Summary",
                "Long abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "developer-experience-innersource",
                List.of(),
                List.of()));

    given()
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/promote")
        .then()
        .statusCode(409)
        .body("error", equalTo("submission_not_accepted"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void invalidStatusReturnsBadRequest() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Async persistence deep dive",
                "Summary",
                "Long abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "data-ai-platforms-llmops",
                List.of(),
                List.of()));

    given()
        .contentType("application/json")
        .body("{\"status\":\"unknown_state\"}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/status")
        .then()
        .statusCode(400)
        .body("error", equalTo("invalid_status"));
  }
}