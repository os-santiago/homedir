package com.scanales.eventflow.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.PersistenceService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CfpSubmissionServiceTest {

  private static final String EVENT_ID = "cfp-event-1";

  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject CfpEventConfigService cfpEventConfigService;
  @Inject EventService eventService;
  @Inject PersistenceService persistenceService;

  @BeforeEach
  void setup() {
    cfpSubmissionService.clearAllForTests();
    cfpEventConfigService.resetForTests();
    eventService.reset();
    Event event = new Event(EVENT_ID, "CFP Event", "Event for CFP tests");
    eventService.saveEvent(event);
  }

  @Test
  void createPersistsAndCanBeLoadedFromPersistence() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Platform Engineering Patterns",
                "A practical session on platform engineering.",
                "The talk shares implementation patterns, anti-patterns and rollout steps.",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                java.util.List.of("platform", "devops"),
                java.util.List.of("https://example.org/slides")));

    assertNotNull(created.id());
    assertEquals(CfpSubmissionStatus.PENDING, created.status());
    assertEquals("platform-engineering-idp", created.track());

    CfpSubmission persisted = persistenceService.loadCfpSubmissions().get(created.id());
    assertNotNull(persisted);
    assertEquals(EVENT_ID, persisted.eventId());
    assertEquals("member@example.com", persisted.proposerUserId());
    assertEquals("platform-engineering-idp", persisted.track());
  }

  @Test
  void createFailsWhenEventDoesNotExist() {
    assertThrows(
        CfpSubmissionService.NotFoundException.class,
        () ->
            cfpSubmissionService.create(
                "member@example.com",
                "Member",
                new CfpSubmissionService.CreateRequest(
                    "missing-event",
                    "Talk",
                    "Summary",
                    "Abstract",
                    "beginner",
                    "talk",
                    30,
                    "en",
                    "platform-engineering-idp",
                    java.util.List.of("java"),
                    java.util.List.of("https://example.org"))));
  }

  @Test
  void createFailsWhenTrackIsInvalid() {
    assertThrows(
        CfpSubmissionService.ValidationException.class,
        () ->
            cfpSubmissionService.create(
                "member@example.com",
                "Member",
                new CfpSubmissionService.CreateRequest(
                    EVENT_ID,
                    "Talk",
                    "Summary",
                    "Abstract",
                    "beginner",
                    "talk",
                    30,
                    "en",
                    "non-existent-track",
                    java.util.List.of(),
                    java.util.List.of())));
  }

  @Test
  void createFailsWhenDurationDoesNotMatchFormat() {
    assertThrows(
        CfpSubmissionService.ValidationException.class,
        () ->
            cfpSubmissionService.create(
                "member@example.com",
                "Member",
                new CfpSubmissionService.CreateRequest(
                    EVENT_ID,
                    "Talk",
                    "Summary",
                    "Abstract",
                    "intermediate",
                    "workshop",
                    60,
                    "en",
                    "platform-engineering-idp",
                    java.util.List.of(),
                    java.util.List.of())));
  }
  @Test
  void statusTransitionUpdatesSubmission() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "SRE for OSS",
                "Summary",
                "Long abstract for SRE topic.",
                "advanced",
                "workshop",
                90,
                "en",
                "cloud-native-security",
                java.util.List.of("sre"),
                java.util.List.of()));

    CfpSubmission underReview =
        cfpSubmissionService.updateStatus(
            created.id(), CfpSubmissionStatus.UNDER_REVIEW, "admin@example.org", "triage");
    CfpSubmission accepted =
        cfpSubmissionService.updateStatus(
            created.id(), CfpSubmissionStatus.ACCEPTED, "admin@example.org", "accepted");

    assertEquals(CfpSubmissionStatus.UNDER_REVIEW, underReview.status());
    assertEquals(CfpSubmissionStatus.ACCEPTED, accepted.status());
    assertNotNull(accepted.updatedAt());
    assertNotNull(accepted.moderatedAt());
    assertEquals("admin@example.org", accepted.moderatedBy());
  }

  @Test
  void updateStatusPersistsModerationNoteEvenWhenStatusIsUnchanged() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "ADev baseline",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                java.util.List.of(),
                java.util.List.of()));

    CfpSubmission first =
        cfpSubmissionService.updateStatus(created.id(), CfpSubmissionStatus.UNDER_REVIEW, "admin@example.org", "triage");
    CfpSubmission updated =
        cfpSubmissionService.updateStatus(
            created.id(), CfpSubmissionStatus.UNDER_REVIEW, "admin@example.org", "needs more details");
    CfpSubmission noChange =
        cfpSubmissionService.updateStatus(
            created.id(), CfpSubmissionStatus.UNDER_REVIEW, "admin@example.org", "   ");

    assertEquals(CfpSubmissionStatus.UNDER_REVIEW, first.status());
    assertEquals(CfpSubmissionStatus.UNDER_REVIEW, updated.status());
    assertEquals("needs more details", updated.moderationNote());
    assertEquals("needs more details", noChange.moderationNote());
  }

  @Test
  void invalidTransitionIsRejected() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Kubernetes deep dive",
                "Summary",
                "Detailed abstract.",
                "advanced",
                "talk",
                30,
                "en",
                "cloud-native-security",
                java.util.List.of("kubernetes"),
                java.util.List.of()));
    cfpSubmissionService.updateStatus(
        created.id(), CfpSubmissionStatus.ACCEPTED, "admin@example.org", "approved");

    assertThrows(
        CfpSubmissionService.InvalidTransitionException.class,
        () ->
            cfpSubmissionService.updateStatus(
                created.id(), CfpSubmissionStatus.PENDING, "admin@example.org", "rollback"));
  }

  @Test
  void rejectStatusRequiresModerationNote() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Reject note guardrail",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                java.util.List.of(),
                java.util.List.of()));

    CfpSubmissionService.ValidationException exception =
        assertThrows(
            CfpSubmissionService.ValidationException.class,
            () ->
                cfpSubmissionService.updateStatus(
                    created.id(), CfpSubmissionStatus.REJECTED, "admin@example.org", " "));

    assertEquals("reject_note_required", exception.getMessage());
  }

  @Test
  void statusUpdateRejectsStaleWriteWhenExpectedVersionDoesNotMatch() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Stale status check",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                java.util.List.of(),
                java.util.List.of()));
    Instant expectedUpdatedAt = created.updatedAt();

    cfpSubmissionService.updateStatus(
        created.id(), CfpSubmissionStatus.UNDER_REVIEW, "admin@example.org", "triage");

    CfpSubmissionService.ValidationException exception =
        assertThrows(
            CfpSubmissionService.ValidationException.class,
            () ->
                cfpSubmissionService.updateStatus(
                    created.id(),
                    CfpSubmissionStatus.ACCEPTED,
                    "admin@example.org",
                    "accepted",
                    expectedUpdatedAt));

    assertEquals("stale_submission", exception.getMessage());
  }

  @Test
  void listByEventSupportsStatusFilter() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Async persistence on JVM",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "developer-experience-innersource",
                java.util.List.of("java"),
                java.util.List.of()));
    cfpSubmissionService.updateStatus(
        created.id(), CfpSubmissionStatus.UNDER_REVIEW, "admin@example.org", "in review");

    var pending = cfpSubmissionService.listByEvent(EVENT_ID, Optional.of(CfpSubmissionStatus.PENDING), 20, 0);
    var inReview =
        cfpSubmissionService.listByEvent(EVENT_ID, Optional.of(CfpSubmissionStatus.UNDER_REVIEW), 20, 0);

    assertTrue(pending.isEmpty());
    assertEquals(1, inReview.size());
    assertEquals(created.id(), inReview.get(0).id());
  }

  @Test
  void createRejectsThirdProposalForSameUserAndEvent() {
    cfpSubmissionService.create(
        "member@example.com",
        "Member",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Talk A",
            "Summary",
            "Abstract",
            "intermediate",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            java.util.List.of(),
            java.util.List.of()));
    cfpSubmissionService.create(
        "member@example.com",
        "Member",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Talk B",
            "Summary",
            "Abstract",
            "intermediate",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            java.util.List.of(),
            java.util.List.of()));

    CfpSubmissionService.ValidationException exception =
        assertThrows(
            CfpSubmissionService.ValidationException.class,
            () ->
                cfpSubmissionService.create(
                    "member@example.com",
                    "Member",
                    new CfpSubmissionService.CreateRequest(
                        EVENT_ID,
                        "Talk C",
                        "Summary",
                        "Abstract",
                        "intermediate",
                        "talk",
                        30,
                        "en",
                        "platform-engineering-idp",
                        java.util.List.of(),
                        java.util.List.of())));

    assertEquals("proposal_limit_reached", exception.getMessage());
  }

  @Test
  void panelistIsVisibleInMineButDoesNotConsumeOwnerQuota() {
    CfpSubmission submission =
        cfpSubmissionService.create(
            "owner@example.com",
            "Owner",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Panel proposal",
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

    List<CfpSubmission> panelistMine =
        cfpSubmissionService.listMine(EVENT_ID, Set.of("panelist@example.com"), 10, 0);
    assertEquals(1, panelistMine.size());
    assertEquals(1, cfpSubmissionService.countMine(EVENT_ID, Set.of("panelist@example.com")));
    assertEquals(0, cfpSubmissionService.countMineOwned(EVENT_ID, Set.of("panelist@example.com")));
  }

  @Test
  void createAppliesEventSpecificMaxSubmissionsLimit() {
    cfpEventConfigService.upsert(
        EVENT_ID,
        new CfpEventConfigService.UpdateRequest(
            true,
            null,
            null,
            1,
            null));

    cfpSubmissionService.create(
        "member@example.com",
        "Member",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Talk A",
            "Summary",
            "Abstract",
            "intermediate",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            java.util.List.of(),
            java.util.List.of()));

    CfpSubmissionService.ValidationException exception =
        assertThrows(
            CfpSubmissionService.ValidationException.class,
            () ->
                cfpSubmissionService.create(
                    "member@example.com",
                    "Member",
                    new CfpSubmissionService.CreateRequest(
                        EVENT_ID,
                        "Talk B",
                        "Summary",
                        "Abstract",
                        "intermediate",
                        "talk",
                        30,
                        "en",
                        "platform-engineering-idp",
                        java.util.List.of(),
                        java.util.List.of())));

    assertEquals("proposal_limit_reached", exception.getMessage());
  }

  @Test
  void createRejectsSubmissionsWhenEventWindowIsClosed() {
    cfpEventConfigService.upsert(
        EVENT_ID,
        new CfpEventConfigService.UpdateRequest(
            false,
            null,
            null,
            null,
            null));

    CfpSubmissionService.ValidationException exception =
        assertThrows(
            CfpSubmissionService.ValidationException.class,
            () ->
                cfpSubmissionService.create(
                    "member@example.com",
                    "Member",
                    new CfpSubmissionService.CreateRequest(
                        EVENT_ID,
                        "Closed window talk",
                        "Summary",
                        "Abstract",
                        "intermediate",
                        "talk",
                        30,
                        "en",
                        "platform-engineering-idp",
                        java.util.List.of(),
                        java.util.List.of())));

    assertEquals("submissions_closed", exception.getMessage());
  }

  @Test
  void createRejectsDuplicateTitleForSameUserAndEvent() {
    cfpSubmissionService.create(
        "member@example.com",
        "Member",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Platform Engineering Patterns",
            "Summary",
            "Abstract",
            "intermediate",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            java.util.List.of(),
            java.util.List.of()));

    CfpSubmissionService.ValidationException exception =
        assertThrows(
            CfpSubmissionService.ValidationException.class,
            () ->
                cfpSubmissionService.create(
                    "member@example.com",
                    "Member",
                    new CfpSubmissionService.CreateRequest(
                        EVENT_ID,
                        "  platform   engineering patterns ",
                        "Summary",
                        "Abstract",
                        "intermediate",
                        "talk",
                        30,
                        "en",
                        "platform-engineering-idp",
                        java.util.List.of(),
                        java.util.List.of())));

    assertEquals("duplicate_title", exception.getMessage());
  }

  @Test
  void deleteRemovesSubmissionAndFreesSlot() {
    CfpSubmission first =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Talk A",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                java.util.List.of(),
                java.util.List.of()));
    cfpSubmissionService.create(
        "member@example.com",
        "Member",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Talk B",
            "Summary",
            "Abstract",
            "intermediate",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            java.util.List.of(),
            java.util.List.of()));

    cfpSubmissionService.delete(EVENT_ID, first.id());

    CfpSubmission replacement =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Talk C",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                java.util.List.of(),
                java.util.List.of()));

    assertNotNull(replacement.id());
  }

  @Test
  void adminCanUpdateRuntimeLimitAndAllowThirdSubmission() {
    assertEquals(2, cfpSubmissionService.currentMaxSubmissionsPerUserPerEvent());
    assertEquals(3, cfpSubmissionService.updateMaxSubmissionsPerUserPerEvent(3));

    cfpSubmissionService.create(
        "member@example.com",
        "Member",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Talk A",
            "Summary",
            "Abstract",
            "intermediate",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            java.util.List.of(),
            java.util.List.of()));
    cfpSubmissionService.create(
        "member@example.com",
        "Member",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Talk B",
            "Summary",
            "Abstract",
            "intermediate",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            java.util.List.of(),
            java.util.List.of()));
    cfpSubmissionService.create(
        "member@example.com",
        "Member",
        new CfpSubmissionService.CreateRequest(
            EVENT_ID,
            "Talk C",
            "Summary",
            "Abstract",
            "intermediate",
            "talk",
            30,
            "en",
            "platform-engineering-idp",
            java.util.List.of(),
            java.util.List.of()));

    CfpSubmissionService.ValidationException exception =
        assertThrows(
            CfpSubmissionService.ValidationException.class,
            () ->
                cfpSubmissionService.create(
                    "member@example.com",
                    "Member",
                    new CfpSubmissionService.CreateRequest(
                        EVENT_ID,
                        "Talk D",
                        "Summary",
                        "Abstract",
                        "intermediate",
                        "talk",
                        30,
                        "en",
                        "platform-engineering-idp",
                        java.util.List.of(),
                        java.util.List.of())));

    assertEquals("proposal_limit_reached", exception.getMessage());
  }

  @Test
  void adminCanRateSubmissionAndComputeWeightedScore() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Observable Platform Engineering",
                "Summary",
                "Abstract",
                "advanced",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                java.util.List.of(),
                java.util.List.of()));

    CfpSubmission rated =
        cfpSubmissionService.updateRating(EVENT_ID, created.id(), 5, 4, 5, "admin@example.org");

    assertEquals(5, rated.ratingTechnicalDetail());
    assertEquals(4, rated.ratingNarrative());
    assertEquals(5, rated.ratingContentImpact());
    assertEquals(4.7d, CfpSubmissionService.calculateWeightedScore(rated));

    CfpSubmission persisted = persistenceService.loadCfpSubmissions().get(created.id());
    assertNotNull(persisted);
    assertEquals(4.7d, CfpSubmissionService.calculateWeightedScore(persisted));
  }

  @Test
  void ratingUpdateRejectsStaleWriteWhenExpectedVersionDoesNotMatch() {
    CfpSubmission created =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Stale rating check",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                java.util.List.of(),
                java.util.List.of()));
    Instant expectedUpdatedAt = created.updatedAt();

    cfpSubmissionService.updateRating(EVENT_ID, created.id(), 4, 4, 4, "admin@example.org");

    CfpSubmissionService.ValidationException exception =
        assertThrows(
            CfpSubmissionService.ValidationException.class,
            () ->
                cfpSubmissionService.updateRating(
                    EVENT_ID,
                    created.id(),
                    5,
                    5,
                    5,
                    "admin@example.org",
                    expectedUpdatedAt));

    assertEquals("stale_submission", exception.getMessage());
  }

  @Test
  void listByEventCanSortByWeightedScore() {
    CfpSubmission low =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Low score talk",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                java.util.List.of(),
                java.util.List.of()));

    CfpSubmission high =
        cfpSubmissionService.create(
            "member@example.com",
            "Member",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "High score talk",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                java.util.List.of(),
                java.util.List.of()));

    cfpSubmissionService.updateRating(EVENT_ID, low.id(), 1, 1, 1, "admin@example.org");
    cfpSubmissionService.updateRating(EVENT_ID, high.id(), 5, 5, 4, "admin@example.org");

    var ordered =
        cfpSubmissionService.listByEvent(
            EVENT_ID,
            Optional.empty(),
            CfpSubmissionService.SortOrder.SCORE_DESC,
            20,
            0);

    assertEquals(2, ordered.size());
    assertEquals(high.id(), ordered.get(0).id());
    assertEquals(low.id(), ordered.get(1).id());
  }

  @Test
  void statsByEventReturnsStatusCountsAndLatestUpdate() {
    CfpSubmission pending =
        cfpSubmissionService.create(
            "member-a@example.com",
            "Member A",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Stats pending",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                java.util.List.of(),
                java.util.List.of()));

    CfpSubmission underReview =
        cfpSubmissionService.create(
            "member-b@example.com",
            "Member B",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Stats review",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                java.util.List.of(),
                java.util.List.of()));
    underReview =
        cfpSubmissionService.updateStatus(
            underReview.id(), CfpSubmissionStatus.UNDER_REVIEW, "admin@example.org", "triage");

    CfpSubmission accepted =
        cfpSubmissionService.create(
            "member-c@example.com",
            "Member C",
            new CfpSubmissionService.CreateRequest(
                EVENT_ID,
                "Stats accepted",
                "Summary",
                "Abstract",
                "intermediate",
                "talk",
                30,
                "en",
                "platform-engineering-idp",
                java.util.List.of(),
                java.util.List.of()));
    accepted =
        cfpSubmissionService.updateStatus(
            accepted.id(), CfpSubmissionStatus.UNDER_REVIEW, "admin@example.org", "review");
    accepted =
        cfpSubmissionService.updateStatus(
            accepted.id(), CfpSubmissionStatus.ACCEPTED, "admin@example.org", "accept");

    CfpSubmissionService.EventStats stats = cfpSubmissionService.statsByEvent(EVENT_ID);
    assertEquals(3, stats.total());
    assertEquals(1, stats.countsByStatus().get(CfpSubmissionStatus.PENDING));
    assertEquals(1, stats.countsByStatus().get(CfpSubmissionStatus.UNDER_REVIEW));
    assertEquals(1, stats.countsByStatus().get(CfpSubmissionStatus.ACCEPTED));
    assertEquals(0, stats.countsByStatus().get(CfpSubmissionStatus.REJECTED));
    assertEquals(0, stats.countsByStatus().get(CfpSubmissionStatus.WITHDRAWN));
    assertNotNull(stats.latestUpdatedAt());

    Instant maxUpdatedAt = Stream.of(pending.updatedAt(), underReview.updatedAt(), accepted.updatedAt())
        .max(Instant::compareTo)
        .orElse(null);
    assertEquals(maxUpdatedAt, stats.latestUpdatedAt());
  }
}
