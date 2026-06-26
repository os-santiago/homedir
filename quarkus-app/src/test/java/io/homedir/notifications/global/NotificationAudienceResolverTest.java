package io.homedir.notifications.global;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.cfp.CfpSubmissionService;
import com.scanales.homedir.cfp.CfpSubmissionStatus;
import com.scanales.homedir.eventops.EventOperationsService;
import com.scanales.homedir.eventops.EventStaffRole;
import com.scanales.homedir.model.Event;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.volunteers.VolunteerApplicationService;
import com.scanales.homedir.volunteers.VolunteerApplicationStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class NotificationAudienceResolverTest {

  @Inject NotificationAudienceResolver resolver;
  @Inject CfpSubmissionService cfpService;
  @Inject VolunteerApplicationService volunteerService;
  @Inject EventOperationsService eventOpsService;
  @Inject EventService eventService;

  private static final String TEST_EVENT_ID = "test-event-segmentation";

  @BeforeEach
  public void setup() {
    eventService.reset();
    // Create test event
    Event event =
        new Event(
            TEST_EVENT_ID,
            "Test Event for Segmentation",
            "Event for testing audience segmentation");
    eventService.saveEvent(event);
  }

  @AfterEach
  public void cleanup() {
    cfpService.clearAllForTests();
    volunteerService.clearAllForTests();
    eventOpsService.clearAllForTests();
  }

  @Test
  public void testNullAudienceReturnsEmptySet() {
    Set<String> result = resolver.resolveAudience(null, TEST_EVENT_ID);
    assertTrue(result.isEmpty(), "Null audience should return empty set (broadcast to all)");
  }

  @Test
  public void testEmptyAudienceReturnsEmptySet() {
    Set<String> result = resolver.resolveAudience("", TEST_EVENT_ID);
    assertTrue(result.isEmpty(), "Empty audience should return empty set (broadcast to all)");
  }

  @Test
  public void testAudienceWithoutEventIdThrowsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> resolver.resolveAudience("cfp", null),
        "Should throw when eventId is null");
  }

  @Test
  public void testCfpAudienceReturnsAcceptedSpeakers() {
    // Create accepted CFP submission
    CfpSubmissionService.CreateRequest req =
        new CfpSubmissionService.CreateRequest(
            TEST_EVENT_ID,
            "Test Talk",
            "Summary",
            "Abstract",
            "beginner",
            "talk",
            30,
            "es",
            "platform-engineering-idp",
            null,
            null);
    var submission = cfpService.create("speaker1@test.com", "Speaker One", req);
    cfpService.updateStatus(
        submission.id(), CfpSubmissionStatus.ACCEPTED, "admin", "Good talk", null);

    Set<String> result = resolver.resolveAudience("cfp", TEST_EVENT_ID);

    assertEquals(1, result.size(), "Should have 1 CFP participant");
    assertTrue(result.contains("speaker1@test.com"), "Should contain the speaker");
  }

  @Test
  public void testCfvAudienceReturnsSelectedVolunteers() {
    // Create selected volunteer
    VolunteerApplicationService.CreateRequest req =
        new VolunteerApplicationService.CreateRequest(
            TEST_EVENT_ID, "About me", "Join reason", "Differentiator");
    var application = volunteerService.create("volunteer1@test.com", "Volunteer One", req);
    volunteerService.updateStatus(
        application.id(), VolunteerApplicationStatus.SELECTED, "admin", "Great volunteer", null);

    Set<String> result = resolver.resolveAudience("cfv", TEST_EVENT_ID);

    assertEquals(1, result.size(), "Should have 1 volunteer");
    assertTrue(result.contains("volunteer1@test.com"), "Should contain the volunteer");
  }

  @Test
  public void testStaffAudienceReturnsActiveStaff() {
    // Create staff member
    eventOpsService.upsertStaff(
        TEST_EVENT_ID, "staff1@test.com", "Staff One", EventStaffRole.ORGANIZER, "manual", true);

    Set<String> result = resolver.resolveAudience("staff", TEST_EVENT_ID);

    assertEquals(1, result.size(), "Should have 1 staff member");
    assertTrue(result.contains("staff1@test.com"), "Should contain the staff member");
  }

  @Test
  public void testMultipleAudiencesAreCombined() {
    // Create one of each
    CfpSubmissionService.CreateRequest cfpReq =
        new CfpSubmissionService.CreateRequest(
            TEST_EVENT_ID,
            "Test Talk",
            "Summary",
            "Abstract",
            "beginner",
            "talk",
            30,
            "es",
            "platform-engineering-idp",
            null,
            null);
    var submission = cfpService.create("speaker@test.com", "Speaker", cfpReq);
    cfpService.updateStatus(submission.id(), CfpSubmissionStatus.ACCEPTED, "admin", "Good", null);

    VolunteerApplicationService.CreateRequest volReq =
        new VolunteerApplicationService.CreateRequest(
            TEST_EVENT_ID, "About me", "Join reason", "Differentiator");
    var application = volunteerService.create("volunteer@test.com", "Volunteer", volReq);
    volunteerService.updateStatus(
        application.id(), VolunteerApplicationStatus.SELECTED, "admin", "Great", null);

    eventOpsService.upsertStaff(
        TEST_EVENT_ID, "staff@test.com", "Staff", EventStaffRole.ORGANIZER, "manual", true);

    Set<String> result = resolver.resolveAudience("cfp,cfv,staff", TEST_EVENT_ID);

    assertEquals(3, result.size(), "Should have 3 unique users");
    assertTrue(result.contains("speaker@test.com"));
    assertTrue(result.contains("volunteer@test.com"));
    assertTrue(result.contains("staff@test.com"));
  }

  @Test
  public void testDuplicateUsersAreDeduped() {
    // User is both speaker and staff
    CfpSubmissionService.CreateRequest req =
        new CfpSubmissionService.CreateRequest(
            TEST_EVENT_ID,
            "Test Talk",
            "Summary",
            "Abstract",
            "beginner",
            "talk",
            30,
            "es",
            "platform-engineering-idp",
            null,
            null);
    var submission = cfpService.create("user@test.com", "User", req);
    cfpService.updateStatus(submission.id(), CfpSubmissionStatus.ACCEPTED, "admin", "Good", null);

    eventOpsService.upsertStaff(
        TEST_EVENT_ID, "user@test.com", "User", EventStaffRole.ORGANIZER, "manual", true);

    Set<String> result = resolver.resolveAudience("cfp,staff", TEST_EVENT_ID);

    assertEquals(1, result.size(), "Should dedupe the same user");
    assertTrue(result.contains("user@test.com"));
  }

  @Test
  public void testEstimateAudienceReturnsCorrectCounts() {
    // Create test data
    CfpSubmissionService.CreateRequest cfpReq =
        new CfpSubmissionService.CreateRequest(
            TEST_EVENT_ID,
            "Test Talk",
            "Summary",
            "Abstract",
            "beginner",
            "talk",
            30,
            "es",
            "platform-engineering-idp",
            null,
            null);
    var submission = cfpService.create("speaker@test.com", "Speaker", cfpReq);
    cfpService.updateStatus(submission.id(), CfpSubmissionStatus.ACCEPTED, "admin", "Good", null);

    VolunteerApplicationService.CreateRequest volReq =
        new VolunteerApplicationService.CreateRequest(
            TEST_EVENT_ID, "About me", "Join reason", "Differentiator");
    var application = volunteerService.create("volunteer@test.com", "Volunteer", volReq);
    volunteerService.updateStatus(
        application.id(), VolunteerApplicationStatus.SELECTED, "admin", "Great", null);

    eventOpsService.upsertStaff(
        TEST_EVENT_ID, "staff@test.com", "Staff", EventStaffRole.ORGANIZER, "manual", true);

    NotificationAudienceResolver.AudienceEstimate estimate =
        resolver.estimateAudience("cfp,cfv,staff", TEST_EVENT_ID);

    assertEquals(3, estimate.total());
    assertEquals(1, estimate.cfpCount());
    assertEquals(1, estimate.cfvCount());
    assertEquals(1, estimate.staffCount());
    assertEquals(false, estimate.global());
  }

  @Test
  public void testEstimateWithNullAudienceIsGlobal() {
    NotificationAudienceResolver.AudienceEstimate estimate =
        resolver.estimateAudience(null, TEST_EVENT_ID);

    assertTrue(estimate.global());
    assertEquals(0, estimate.total());
  }
}
