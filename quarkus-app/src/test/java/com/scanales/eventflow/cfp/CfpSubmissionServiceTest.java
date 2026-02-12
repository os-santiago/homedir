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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class CfpSubmissionServiceTest {

  private static final String EVENT_ID = "cfp-event-1";

  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject EventService eventService;
  @Inject PersistenceService persistenceService;

  @BeforeEach
  void setup() {
    cfpSubmissionService.clearAllForTests();
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
                45,
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
                60,
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
}