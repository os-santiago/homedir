package com.scanales.eventflow.volunteers;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class VolunteerApplicationServiceTest {

  private static final String EVENT_ID = "volunteer-event-1";

  @Inject VolunteerApplicationService volunteerApplicationService;
  @Inject VolunteerEventConfigService volunteerEventConfigService;
  @Inject EventService eventService;
  @Inject PersistenceService persistenceService;

  @BeforeEach
  void setup() {
    volunteerApplicationService.clearAllForTests();
    volunteerEventConfigService.resetForTests();
    eventService.reset();
    eventService.saveEvent(new Event(EVENT_ID, "Volunteer Event", "Volunteer tests"));
  }

  @Test
  void createPersistsAndCanBeLoadedFromPersistence() {
    VolunteerApplication created =
        volunteerApplicationService.create(
            "member@example.com",
            "Member",
            new VolunteerApplicationService.CreateRequest(
                EVENT_ID,
                "I support open source operations and community logistics.",
                "I want to collaborate with the event team.",
                "I can coordinate speaker logistics."));

    assertNotNull(created.id());
    assertEquals(VolunteerApplicationStatus.APPLIED, created.status());

    VolunteerApplication persisted = persistenceService.loadVolunteerApplications().get(created.id());
    assertNotNull(persisted);
    assertEquals(EVENT_ID, persisted.eventId());
    assertEquals("member@example.com", persisted.applicantUserId());
  }

  @Test
  void createFailsWhenAlreadyApplied() {
    volunteerApplicationService.create(
        "member@example.com",
        "Member",
        new VolunteerApplicationService.CreateRequest(
            EVENT_ID,
            "About me",
            "Join reason",
            "Differentiator"));

    VolunteerApplicationService.ValidationException ex =
        assertThrows(
            VolunteerApplicationService.ValidationException.class,
            () ->
                volunteerApplicationService.create(
                    "member@example.com",
                    "Member",
                    new VolunteerApplicationService.CreateRequest(
                        EVENT_ID,
                        "About me",
                        "Join reason",
                        "Differentiator")));

    assertEquals("already_applied", ex.getMessage());
  }

  @Test
  void createFailsWhenWindowIsClosed() {
    volunteerEventConfigService.upsert(
        EVENT_ID,
        new VolunteerEventConfigService.UpdateRequest(false, null, null));

    VolunteerApplicationService.ValidationException ex =
        assertThrows(
            VolunteerApplicationService.ValidationException.class,
            () ->
                volunteerApplicationService.create(
                    "member@example.com",
                    "Member",
                    new VolunteerApplicationService.CreateRequest(
                        EVENT_ID,
                        "About me",
                        "Join reason",
                        "Differentiator")));

    assertEquals("submissions_closed", ex.getMessage());
  }

  @Test
  void statusTransitionsAndRejectNoteValidation() {
    VolunteerApplication created =
        volunteerApplicationService.create(
            "member@example.com",
            "Member",
            new VolunteerApplicationService.CreateRequest(
                EVENT_ID,
                "About me",
                "Join reason",
                "Differentiator"));

    VolunteerApplication review =
        volunteerApplicationService.updateStatus(
            created.id(),
            VolunteerApplicationStatus.UNDER_REVIEW,
            "admin@example.com",
            "triage",
            null);
    assertEquals(VolunteerApplicationStatus.UNDER_REVIEW, review.status());

    VolunteerApplicationService.ValidationException rejectEx =
        assertThrows(
            VolunteerApplicationService.ValidationException.class,
            () ->
                volunteerApplicationService.updateStatus(
                    created.id(),
                    VolunteerApplicationStatus.NOT_SELECTED,
                    "admin@example.com",
                    " ",
                    null));
    assertEquals("reject_note_required", rejectEx.getMessage());

    VolunteerApplication rejected =
        volunteerApplicationService.updateStatus(
            created.id(),
            VolunteerApplicationStatus.NOT_SELECTED,
            "admin@example.com",
            "No capacity this cycle",
            null);
    assertEquals(VolunteerApplicationStatus.NOT_SELECTED, rejected.status());
    assertEquals("No capacity this cycle", rejected.moderationNote());
  }

  @Test
  void ownerCanUpdateAndWithdrawBeforeDecision() {
    VolunteerApplication created =
        volunteerApplicationService.create(
            "member@example.com",
            "Member",
            new VolunteerApplicationService.CreateRequest(
                EVENT_ID,
                "About me",
                "Join reason",
                "Differentiator"));

    VolunteerApplication updated =
        volunteerApplicationService.updateMine(
            created.id(),
            EVENT_ID,
            "member@example.com",
            new VolunteerApplicationService.UpdateRequest(
                "Updated about me",
                "Updated join reason",
                "Updated differentiator"));
    assertEquals("Updated about me", updated.aboutMe());

    VolunteerApplication withdrawn =
        volunteerApplicationService.withdrawMine(created.id(), EVENT_ID, "member@example.com");
    assertEquals(VolunteerApplicationStatus.WITHDRAWN, withdrawn.status());
  }

  @Test
  void updateMineRejectedAfterDecision() {
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
        "admin@example.com",
        "Great fit",
        null);

    VolunteerApplicationService.InvalidTransitionException ex =
        assertThrows(
            VolunteerApplicationService.InvalidTransitionException.class,
            () ->
                volunteerApplicationService.updateMine(
                    created.id(),
                    EVENT_ID,
                    "member@example.com",
                    new VolunteerApplicationService.UpdateRequest(
                        "Updated about me",
                        "Updated join reason",
                        "Updated differentiator")));
    assertEquals("immutable_after_decision", ex.getMessage());
  }

  @Test
  void ratingUpdateCalculatesWeightedScoreAndSortsByScore() {
    VolunteerApplication low =
        volunteerApplicationService.create(
            "member-a@example.com",
            "Member A",
            new VolunteerApplicationService.CreateRequest(EVENT_ID, "A", "A", "A"));
    VolunteerApplication high =
        volunteerApplicationService.create(
            "member-b@example.com",
            "Member B",
            new VolunteerApplicationService.CreateRequest(EVENT_ID, "B", "B", "B"));

    low =
        volunteerApplicationService.updateRating(
            EVENT_ID, low.id(), 1, 1, 1, "admin@example.com", null);
    high =
        volunteerApplicationService.updateRating(
            EVENT_ID, high.id(), 5, 5, 4, "admin@example.com", null);

    assertEquals(1.0d, VolunteerApplicationService.calculateWeightedScore(low));
    assertEquals(4.7d, VolunteerApplicationService.calculateWeightedScore(high));

    var ordered =
        volunteerApplicationService.listByEvent(
            EVENT_ID,
            Optional.empty(),
            VolunteerApplicationService.SortOrder.SCORE_DESC,
            20,
            0);
    assertEquals(2, ordered.size());
    assertEquals(high.id(), ordered.get(0).id());
    assertEquals(low.id(), ordered.get(1).id());
  }

  @Test
  void statusUpdateRejectsStaleVersion() {
    VolunteerApplication created =
        volunteerApplicationService.create(
            "member@example.com",
            "Member",
            new VolunteerApplicationService.CreateRequest(EVENT_ID, "About me", "Join reason", "Diff"));
    Instant expected = created.updatedAt();

    volunteerApplicationService.updateStatus(
        created.id(), VolunteerApplicationStatus.UNDER_REVIEW, "admin@example.com", "triage", null);

    VolunteerApplicationService.ValidationException ex =
        assertThrows(
            VolunteerApplicationService.ValidationException.class,
            () ->
                volunteerApplicationService.updateStatus(
                    created.id(),
                    VolunteerApplicationStatus.SELECTED,
                    "admin@example.com",
                    "ok",
                    expected));
    assertEquals("stale_submission", ex.getMessage());
  }

  @Test
  void statsByEventReflectsCurrentStatuses() {
    VolunteerApplication applied =
        volunteerApplicationService.create(
            "member-a@example.com",
            "Member A",
            new VolunteerApplicationService.CreateRequest(EVENT_ID, "A", "A", "A"));
    VolunteerApplication review =
        volunteerApplicationService.create(
            "member-b@example.com",
            "Member B",
            new VolunteerApplicationService.CreateRequest(EVENT_ID, "B", "B", "B"));
    VolunteerApplication selected =
        volunteerApplicationService.create(
            "member-c@example.com",
            "Member C",
            new VolunteerApplicationService.CreateRequest(EVENT_ID, "C", "C", "C"));

    review =
        volunteerApplicationService.updateStatus(
            review.id(),
            VolunteerApplicationStatus.UNDER_REVIEW,
            "admin@example.com",
            "triage",
            null);
    selected =
        volunteerApplicationService.updateStatus(
            selected.id(),
            VolunteerApplicationStatus.SELECTED,
            "admin@example.com",
            "selected",
            null);

    VolunteerApplicationService.EventStats stats = volunteerApplicationService.statsByEvent(EVENT_ID);
    assertEquals(3, stats.total());
    assertEquals(1, stats.countsByStatus().get(VolunteerApplicationStatus.APPLIED));
    assertEquals(1, stats.countsByStatus().get(VolunteerApplicationStatus.UNDER_REVIEW));
    assertEquals(1, stats.countsByStatus().get(VolunteerApplicationStatus.SELECTED));
    assertNotNull(stats.latestUpdatedAt());

    Instant expectedLatest =
        java.util.stream.Stream.of(applied.updatedAt(), review.updatedAt(), selected.updatedAt())
            .max(Instant::compareTo)
            .orElse(null);
    assertEquals(expectedLatest, stats.latestUpdatedAt());
  }

  @Test
  void configWindowValidationRejectsInvalidRange() {
    VolunteerEventConfigService.ValidationException ex =
        assertThrows(
            VolunteerEventConfigService.ValidationException.class,
            () ->
                volunteerEventConfigService.upsert(
                    EVENT_ID,
                    new VolunteerEventConfigService.UpdateRequest(
                        true,
                        Instant.parse("2030-02-01T00:00:00Z"),
                        Instant.parse("2030-01-01T00:00:00Z"))));
    assertEquals("invalid_window", ex.getMessage());
  }
}
