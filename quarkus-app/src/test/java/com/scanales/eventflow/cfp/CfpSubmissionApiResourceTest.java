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
              "duration_min":30,
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
  void createAcceptsLanguageLabel() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"Language label support",
              "summary":"Summary",
              "abstract_text":"Abstract",
              "level":"beginner",
              "format":"talk",
              "duration_min":30,
              "language":"English",
              "track":"platform-engineering-idp"
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions")
        .then()
        .statusCode(201)
        .body("item.language", equalTo("en"));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void createRejectsDurationThatDoesNotMatchFormat() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"Mismatched duration",
              "summary":"Summary",
              "abstract_text":"Abstract",
              "level":"beginner",
              "format":"workshop",
              "duration_min":60,
              "language":"en",
              "track":"platform-engineering-idp"
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions")
        .then()
        .statusCode(400)
        .body("error", equalTo("invalid_duration"));
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
              "duration_min":30,
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
                30,
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
                30,
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

  @Test
  @TestSecurity(user = "member@example.com")
  void createRejectsThirdProposalWithConflict() {
    for (int i = 1; i <= 2; i++) {
      given()
          .contentType("application/json")
          .body(
              """
              {
                "title":"Talk %d",
                "summary":"Summary",
                "abstract_text":"Abstract",
                "level":"intermediate",
                "format":"talk",
                "duration_min":30,
                "language":"en",
                "track":"platform-engineering-idp"
              }
              """.formatted(i))
          .when()
          .post("/api/events/" + EVENT_ID + "/cfp/submissions")
          .then()
          .statusCode(201);
    }

    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"Talk 3",
              "summary":"Summary",
              "abstract_text":"Abstract",
              "level":"intermediate",
              "format":"talk",
              "duration_min":30,
              "language":"en",
              "track":"platform-engineering-idp"
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions")
        .then()
        .statusCode(409)
        .body("error", equalTo("proposal_limit_reached"));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void createRejectsDuplicateTitleWithConflict() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"Reliable Platform Delivery",
              "summary":"Summary",
              "abstract_text":"Abstract",
              "level":"intermediate",
              "format":"talk",
              "duration_min":30,
              "language":"en",
              "track":"platform-engineering-idp"
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions")
        .then()
        .statusCode(201);

    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"  reliable   platform delivery ",
              "summary":"Summary",
              "abstract_text":"Abstract",
              "level":"intermediate",
              "format":"talk",
              "duration_min":30,
              "language":"en",
              "track":"platform-engineering-idp"
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions")
        .then()
        .statusCode(409)
        .body("error", equalTo("duplicate_title"));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void ownerCanDeleteOwnSubmission() {
    String submissionId =
        given()
            .contentType("application/json")
            .body(
                """
                {
                  "title":"Delete me",
                  "summary":"Summary",
                  "abstract_text":"Abstract",
                  "level":"intermediate",
                  "format":"talk",
                  "duration_min":30,
                  "language":"en",
                  "track":"platform-engineering-idp"
                }
                """)
            .when()
            .post("/api/events/" + EVENT_ID + "/cfp/submissions")
            .then()
            .statusCode(201)
            .extract()
            .path("item.id");

    given()
        .when()
        .delete("/api/events/" + EVENT_ID + "/cfp/submissions/" + submissionId)
        .then()
        .statusCode(200)
        .body("item.id", equalTo(submissionId));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/mine?limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("items", hasSize(0));
  }

  @Test
  @TestSecurity(user = "other-member@example.com")
  void nonOwnerCannotDeleteSubmission() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Protected talk",
                "Summary",
                "Long abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of()));

    given()
        .when()
        .delete("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id())
        .then()
        .statusCode(403)
        .body("error", equalTo("owner_required"));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void configEndpointExposesCurrentLimit() {
    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/config")
        .then()
        .statusCode(200)
        .body("max_per_user", equalTo(2))
        .body("min_allowed", equalTo(1))
        .body("max_allowed", equalTo(10));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void nonAdminCannotUpdateLimitConfig() {
    given()
        .contentType("application/json")
        .body("{\"max_per_user\":3}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/config")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanUpdateLimitConfig() {
    given()
        .contentType("application/json")
        .body("{\"max_per_user\":3}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/config")
        .then()
        .statusCode(200)
        .body("max_per_user", equalTo(3));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/config")
        .then()
        .statusCode(200)
        .body("max_per_user", equalTo(3));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminLimitConfigRejectsOutOfRangeValues() {
    given()
        .contentType("application/json")
        .body("{\"max_per_user\":0}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/config")
        .then()
        .statusCode(400)
        .body("error", equalTo("invalid_limit"));

    given()
        .contentType("application/json")
        .body("{\"max_per_user\":11}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/config")
        .then()
        .statusCode(400)
        .body("error", equalTo("invalid_limit"));
  }}

