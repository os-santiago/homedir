package com.scanales.eventflow.cfp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.eventflow.insights.DevelopmentInsightsLedgerService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.SpeakerService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CfpSubmissionApiResourceTest {

  private static final String EVENT_ID = "cfp-api-event-1";

  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject CfpEventConfigService cfpEventConfigService;
  @Inject EventService eventService;
  @Inject SpeakerService speakerService;
  @Inject DevelopmentInsightsLedgerService insightsLedger;

  @BeforeEach
  void setup() {
    cfpSubmissionService.clearAllForTests();
    cfpEventConfigService.resetForTests();
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
        .body("total", equalTo(1))
        .body("has_more", equalTo(false))
        .body("next_offset", org.hamcrest.Matchers.nullValue())
        .body("items", hasSize(1))
        .body("items[0].title", equalTo("Cloud Native Platform Stories"))
        .body("items[0].track", equalTo("platform-engineering-idp"));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void createSubmissionWritesAutomaticInsightsInitiative() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"Insights integration for CFP",
              "summary":"Automatic insights capture",
              "abstract_text":"Testing automatic insights events for CFP module.",
              "level":"intermediate",
              "format":"talk",
              "duration_min":30,
              "language":"en",
              "track":"platform-engineering-idp",
              "links":["https://example.org/insights"]
            }
            """)
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions")
        .then()
        .statusCode(201);

    String expectedInitiativeId = "event-cfp-cfp-api-event-1";
    boolean exists =
        insightsLedger.listInitiatives(200, 0).stream()
            .anyMatch(item -> expectedInitiativeId.equals(item.initiativeId()) && item.totalEvents() > 0);
    assertTrue(exists, "automatic CFP insights initiative should exist with recorded events");
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void moderationListExposesPaginationMetadata() {
    cfpSubmissionService.create(
        "member-1@example.com",
        "Member 1",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Submission one",
            "Summary",
            "Abstract",
            "beginner",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            List.of(),
            List.of()));
    cfpSubmissionService.create(
        "member-2@example.com",
        "Member 2",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Submission two",
            "Summary",
            "Abstract",
            "beginner",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            List.of(),
            List.of()));
    cfpSubmissionService.create(
        "member-3@example.com",
        "Member 3",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Submission three",
            "Summary",
            "Abstract",
            "beginner",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            List.of(),
            List.of()));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions?status=all&sort=created&limit=2&offset=0")
        .then()
        .statusCode(200)
        .body("limit", equalTo(2))
        .body("offset", equalTo(0))
        .body("total", equalTo(3))
        .body("has_more", equalTo(true))
        .body("next_offset", equalTo(2))
        .body("items", hasSize(2));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void moderationListSupportsUpdatedSort() {
    CfpSubmission older =
        cfpSubmissionService.create(
            "member-1@example.com",
            "Member 1",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Older submission",
                "Summary",
                "Abstract",
                "beginner",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of()));
    CfpSubmission newer =
        cfpSubmissionService.create(
            "member-2@example.com",
            "Member 2",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Newer submission",
                "Summary",
                "Abstract",
                "beginner",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of()));

    cfpSubmissionService.updateStatus(
        older.id(), CfpSubmissionStatus.UNDER_REVIEW, "admin@example.org", "triage");

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions?status=all&sort=updated&limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("items", hasSize(2))
        .body("items[0].id", equalTo(older.id()))
        .body("items[1].id", equalTo(newer.id()));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void mineEndpointExposesPaginationMetadata() {
    cfpSubmissionService.create(
        "member@example.com",
        "Member",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Mine one",
            "Summary",
            "Abstract",
            "beginner",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            List.of(),
            List.of()));
    cfpSubmissionService.create(
        "member@example.com",
        "Member",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Mine two",
            "Summary",
            "Abstract",
            "beginner",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            List.of(),
            List.of()));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/mine?limit=1&offset=0")
        .then()
        .statusCode(200)
        .body("limit", equalTo(1))
        .body("offset", equalTo(0))
        .body("total", equalTo(2))
        .body("has_more", equalTo(true))
        .body("next_offset", equalTo(1))
        .body("items", hasSize(1));
  }

  @Test
  @TestSecurity(user = "panelist@example.com")
  void mineEndpointIncludesPanelistEntriesAndOwnedTotal() {
    CfpSubmission submission =
        cfpSubmissionService.create(
            "owner@example.com",
            "Owner",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Panel API proposal",
                "Summary",
                "Panel abstract",
                "intermediate",
                "panel",
                60,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of()));
    cfpSubmissionService.updatePanelists(
        EVENT_ID,
        submission.id(),
        List.of(new CfpSubmissionService.PanelistInput("Panelist", "panelist@example.com", "panelist@example.com")),
        "owner@example.com",
        null);

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/mine?limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("owned_total", equalTo(0))
        .body("items", hasSize(1))
        .body("items[0].viewer_role", equalTo("panelist"))
        .body("items[0].can_edit", equalTo(false));
  }

  @Test
  @TestSecurity(user = "panelist@example.com")
  void panelistCanUploadPresentationForAcceptedPanel() {
    CfpSubmission submission =
        cfpSubmissionService.create(
            "owner@example.com",
            "Owner",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Panel upload proposal",
                "Summary",
                "Panel abstract",
                "intermediate",
                "panel",
                60,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of()));
    cfpSubmissionService.updatePanelists(
        EVENT_ID,
        submission.id(),
        List.of(new CfpSubmissionService.PanelistInput("Panelist", "panelist@example.com", "panelist@example.com")),
        "owner@example.com",
        null);
    cfpSubmissionService.updateStatus(
        submission.id(), CfpSubmissionStatus.ACCEPTED, "admin@example.org", "accepted");

    given()
        .multiPart("file", "slides.pdf", "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/pdf")
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions/" + submission.id() + "/presentation")
        .then()
        .statusCode(200)
        .body("item.presentation_asset.file_name", org.hamcrest.Matchers.notNullValue())
        .body("item.viewer_role", equalTo("panelist"));
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

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/stats")
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
  void adminCanFilterReviewQueueByProposerTitleAndTrack() {
    cfpSubmissionService.create(
        "speaker-alpha@example.com",
        "Speaker Alpha",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Platform scorecards in production",
            "Summary",
            "Long abstract",
            "advanced",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            List.of("platform"),
            List.of("https://example.org/platform")));
    cfpSubmissionService.create(
        "speaker-beta@example.com",
        "Speaker Beta",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "DevOps collaboration stories",
            "Summary",
            "Long abstract",
            "beginner",
            "talk",
            30,
            "en",
            "devops",
            List.of("devops"),
            List.of("https://example.org/devops")));

    given()
        .accept("application/json")
        .queryParam("status", "all")
        .queryParam("proposed_by", "alpha")
        .queryParam("title", "scorecards")
        .queryParam("track", "platform")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions?limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].proposer_name", equalTo("Speaker Alpha"))
        .body("items[0].title", equalTo("Platform scorecards in production"))
        .body("items[0].track", equalTo("platform-engineering-idp"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void rejectedSubmissionRemainsVisibleWithUpdatedStatusAndRatingUnderFilters() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "speaker-alpha@example.com",
            "Speaker Alpha",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Platform scorecards in production",
                "Summary",
                "Long abstract",
                "advanced",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of("platform"),
                List.of("https://example.org/platform")));

    given()
        .contentType("application/json")
        .body("{\"technical_detail\":4,\"narrative\":5,\"content_impact\":3}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/rating")
        .then()
        .statusCode(200)
        .body("item.rating_weighted", equalTo(4.0f));

    given()
        .contentType("application/json")
        .body("{\"status\":\"rejected\",\"note\":\"not the right fit for this edition\"}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/status")
        .then()
        .statusCode(200)
        .body("item.status", equalTo("rejected"))
        .body("item.moderation_note", equalTo("not the right fit for this edition"));

    given()
        .accept("application/json")
        .queryParam("status", "rejected")
        .queryParam("proposed_by", "alpha")
        .queryParam("title", "scorecards")
        .queryParam("track", "platform")
        .queryParam("limit", 10)
        .queryParam("offset", 0)
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].id", equalTo(created.id()))
        .body("items[0].status", equalTo("rejected"))
        .body("items[0].moderation_note", equalTo("not the right fit for this edition"))
        .body("items[0].rating_weighted", equalTo(4.0f));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanReadModerationStats() {
    CfpSubmission pending =
        cfpSubmissionService.create(
            "member-a@example.com",
            "Member A",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Stats pending api",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of()));

    CfpSubmission underReview =
        cfpSubmissionService.create(
            "member-b@example.com",
            "Member B",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Stats review api",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of()));
    cfpSubmissionService.updateStatus(
        underReview.id(), CfpSubmissionStatus.UNDER_REVIEW, "admin@example.org", "triage");

    CfpSubmission accepted =
        cfpSubmissionService.create(
            "member-c@example.com",
            "Member C",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Stats accepted api",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of()));
    cfpSubmissionService.updateStatus(
        accepted.id(), CfpSubmissionStatus.UNDER_REVIEW, "admin@example.org", "review");
    cfpSubmissionService.updateStatus(
        accepted.id(), CfpSubmissionStatus.ACCEPTED, "admin@example.org", "approved");

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/stats")
        .then()
        .statusCode(200)
        .body("total", equalTo(3))
        .body("pending", equalTo(1))
        .body("under_review", equalTo(1))
        .body("accepted", equalTo(1))
        .body("rejected", equalTo(0))
        .body("withdrawn", equalTo(0))
        .body("latest_updated_at", org.hamcrest.Matchers.notNullValue());
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanReadSubmissionDetailById() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Detailed proposal",
                "Short summary",
                "Long abstract for the admin detail page.",
                "advanced",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of("https://example.org/detail")));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id())
        .then()
        .statusCode(200)
        .body("item.id", equalTo(created.id()))
        .body("item.title", equalTo("Detailed proposal"))
        .body("item.summary", equalTo("Short summary"))
        .body("item.abstract_text", equalTo("Long abstract for the admin detail page."));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void statusUpdateRejectsStaleVersionConflict() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Conflict status proposal",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of()));
    Instant staleVersion = created.updatedAt();
    cfpSubmissionService.updateStatus(
        created.id(), CfpSubmissionStatus.UNDER_REVIEW, "admin@example.org", "triage");

    given()
        .contentType("application/json")
        .body(
            """
            {
              "status":"accepted",
              "note":"approved",
              "expected_updated_at":"%s"
            }
            """.formatted(staleVersion))
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/status")
        .then()
        .statusCode(409)
        .body("error", equalTo("stale_submission"));
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
  void ratingUpdateRejectsStaleVersionConflict() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Conflict rating proposal",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of()));
    Instant staleVersion = created.updatedAt();
    cfpSubmissionService.updateRating(EVENT_ID, created.id(), 2, 2, 2, "admin@example.org");

    given()
        .contentType("application/json")
        .body(
            """
            {
              "technical_detail":5,
              "narrative":5,
              "content_impact":5,
              "expected_updated_at":"%s"
            }
            """.formatted(staleVersion))
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/rating")
        .then()
        .statusCode(409)
        .body("error", equalTo("stale_submission"));
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
  @TestSecurity(user = "admin@example.org")
  void rejectStatusWithoutNoteReturnsBadRequest() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Reject note required",
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
        .body("{\"status\":\"rejected\",\"note\":\"   \"}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/status")
        .then()
        .statusCode(400)
        .body("error", equalTo("reject_note_required"));
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
  void createRejectsWhenEventSubmissionsAreClosed() {
    cfpEventConfigService.upsert(
        EVENT_ID,
        new CfpEventConfigService.UpdateRequest(
            false,
            null,
            null,
            null,
            null));

    given()
        .contentType("application/json")
        .body(
            """
            {
              "title":"Talk during closed CFP",
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
        .body("error", equalTo("submissions_closed"));
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
  void nonAdminCannotReadStorageStatus() {
    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/storage")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void nonAdminCannotRunStorageRepair() {
    given()
        .accept("application/json")
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions/storage/repair?dry_run=true")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanReadStorageStatus() {
    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/storage")
        .then()
        .statusCode(200)
        .body("primary_path", org.hamcrest.Matchers.notNullValue())
        .body("backups_path", org.hamcrest.Matchers.notNullValue())
        .body("backup_count", org.hamcrest.Matchers.greaterThanOrEqualTo(0))
        .body("primary_valid", org.hamcrest.Matchers.notNullValue())
        .body("primary_missing_checksum", org.hamcrest.Matchers.notNullValue())
        .body("backup_valid_count", org.hamcrest.Matchers.greaterThanOrEqualTo(0))
        .body("backup_invalid_count", org.hamcrest.Matchers.greaterThanOrEqualTo(0))
        .body("backup_missing_checksum_count", org.hamcrest.Matchers.greaterThanOrEqualTo(0))
        .body("wal_enabled", org.hamcrest.Matchers.notNullValue())
        .body("wal_path", org.hamcrest.Matchers.notNullValue())
        .body("checksum_enabled", org.hamcrest.Matchers.notNullValue())
        .body("checksum_required", org.hamcrest.Matchers.notNullValue())
        .body("checksum_mismatches", org.hamcrest.Matchers.greaterThanOrEqualTo(0))
        .body("checksum_hydrations", org.hamcrest.Matchers.greaterThanOrEqualTo(0));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanRunStorageRepairDryRun() {
    given()
        .accept("application/json")
        .when()
        .post("/api/events/" + EVENT_ID + "/cfp/submissions/storage/repair?dry_run=true")
        .then()
        .statusCode(200)
        .body("dry_run", equalTo(true))
        .body("backups_scanned", org.hamcrest.Matchers.greaterThanOrEqualTo(0))
        .body("backups_valid", org.hamcrest.Matchers.greaterThanOrEqualTo(0))
        .body("backups_needing_repair", org.hamcrest.Matchers.greaterThanOrEqualTo(0))
        .body("backups_quarantine_candidates", org.hamcrest.Matchers.greaterThanOrEqualTo(0))
        .body("errors", org.hamcrest.Matchers.greaterThanOrEqualTo(0));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void nonAdminCannotUpdateRating() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Rating guard",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of()));

    given()
        .contentType("application/json")
        .body("{\"technical_detail\":4,\"narrative\":4,\"content_impact\":4}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/" + created.id() + "/rating")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanRateSortAndExportCsv() {
    CfpSubmission low =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Low score submission",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of()));

    CfpSubmission high =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "High score submission",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                List.of(),
                List.of()));

    given()
        .contentType("application/json")
        .body("{\"technical_detail\":1,\"narrative\":1,\"content_impact\":1}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/" + low.id() + "/rating")
        .then()
        .statusCode(200)
        .body("item.rating_weighted", equalTo(1.0f));

    given()
        .contentType("application/json")
        .body("{\"technical_detail\":5,\"narrative\":5,\"content_impact\":4}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/" + high.id() + "/rating")
        .then()
        .statusCode(200)
        .body("item.rating_weighted", equalTo(4.7f));

    given()
        .contentType("application/json")
        .body("{\"status\":\"accepted\",\"note\":\"approved by jury\"}")
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/" + high.id() + "/status")
        .then()
        .statusCode(200)
        .body("item.status", equalTo("accepted"))
        .body("item.moderated_by", equalTo("admin@example.org"));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions?status=all&sort=score&limit=10&offset=0")
        .then()
        .statusCode(200)
        .body("total", equalTo(2))
        .body("has_more", equalTo(false))
        .body("next_offset", org.hamcrest.Matchers.nullValue())
        .body("items", hasSize(2))
        .body("items[0].id", equalTo(high.id()))
        .body("items[1].id", equalTo(low.id()));

    given()
        .accept("text/csv")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/export.csv?status=all&sort=score")
        .then()
        .statusCode(200)
        .contentType(containsString("text/csv"))
        .body(containsString("rating_weighted"))
        .body(containsString("updated_at"))
        .body(containsString("moderated_at"))
        .body(containsString("moderated_by"))
        .body(containsString("admin@example.org"))
        .body(containsString("approved by jury"))
        .body(containsString("High score submission"));
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
        .body("max_allowed", equalTo(10))
        .body("currently_open", equalTo(true))
        .body("has_event_override", equalTo(false));
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void configEndpointReflectsEventSpecificOverride() {
    cfpEventConfigService.upsert(
        EVENT_ID,
        new CfpEventConfigService.UpdateRequest(
            false,
            null,
            null,
            4,
            false));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/config")
        .then()
        .statusCode(200)
        .body("max_per_user", equalTo(4))
        .body("testing_mode_enabled", equalTo(false))
        .body("accepting_submissions", equalTo(false))
        .body("currently_open", equalTo(false))
        .body("has_event_override", equalTo(true));
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
  }

  @Test
  @TestSecurity(user = "member@example.com")
  void nonAdminCannotReadEventConfig() {
    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/event-config")
        .then()
        .statusCode(403)
        .body("error", equalTo("admin_required"));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanReadDefaultEventConfig() {
    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/event-config")
        .then()
        .statusCode(200)
        .body("event_id", equalTo(EVENT_ID))
        .body("has_override", equalTo(false))
        .body("effective.max_per_user", equalTo(2))
        .body("effective.testing_mode_enabled", equalTo(true))
        .body("effective.accepting_submissions", equalTo(true))
        .body("effective.currently_open", equalTo(true));
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void adminCanUpdateAndClearEventConfig() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "accepting_submissions": false,
              "opens_at": "2030-01-01T09:00:00Z",
              "closes_at": "2030-01-31T23:59:59Z",
              "max_per_user": 4,
              "testing_mode_enabled": false
            }
            """)
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/event-config")
        .then()
        .statusCode(200)
        .body("event_id", equalTo(EVENT_ID))
        .body("has_override", equalTo(true))
        .body("override.accepting_submissions", equalTo(false))
        .body("override.max_per_user", equalTo(4))
        .body("override.testing_mode_enabled", equalTo(false))
        .body("effective.max_per_user", equalTo(4))
        .body("effective.testing_mode_enabled", equalTo(false))
        .body("effective.currently_open", equalTo(false));

    given()
        .accept("application/json")
        .when()
        .delete("/api/events/" + EVENT_ID + "/cfp/submissions/event-config")
        .then()
        .statusCode(200)
        .body("event_id", equalTo(EVENT_ID))
        .body("cleared", equalTo(true))
        .body("effective.max_per_user", equalTo(2))
        .body("effective.testing_mode_enabled", equalTo(true));

    given()
        .accept("application/json")
        .when()
        .get("/api/events/" + EVENT_ID + "/cfp/submissions/event-config")
        .then()
        .statusCode(200)
        .body("has_override", equalTo(false))
        .body("override", org.hamcrest.Matchers.nullValue());
  }

  @Test
  @TestSecurity(user = "admin@example.org")
  void eventConfigRejectsInvalidWindow() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "opens_at": "2030-02-01T00:00:00Z",
              "closes_at": "2030-01-01T00:00:00Z"
            }
            """)
        .when()
        .put("/api/events/" + EVENT_ID + "/cfp/submissions/event-config")
        .then()
        .statusCode(400)
        .body("error", equalTo("invalid_window"));
  }
}
