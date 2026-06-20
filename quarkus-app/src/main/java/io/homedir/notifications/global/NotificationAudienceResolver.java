package io.homedir.notifications.global;

import com.scanales.homedir.cfp.CfpSubmissionService;
import com.scanales.homedir.cfp.CfpSubmissionStatus;
import com.scanales.homedir.eventops.EventOperationsService;
import com.scanales.homedir.volunteers.VolunteerApplicationService;
import com.scanales.homedir.volunteers.VolunteerApplicationStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Service for resolving notification audiences to sets of user IDs based on their roles and
 * participation in events.
 */
@ApplicationScoped
public class NotificationAudienceResolver {

  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject VolunteerApplicationService volunteerApplicationService;
  @Inject EventOperationsService eventOperationsService;

  /**
   * Resolves a notification audience specification to a set of user IDs.
   *
   * @param audience the audience specification (e.g., "cfp,cfv,staff" or null for all)
   * @param eventId the event ID (required if audience is specified)
   * @return set of user IDs that match the audience criteria, or empty set if audience is null
   *     (broadcast to all)
   */
  public Set<String> resolveAudience(String audience, String eventId) {
    if (audience == null || audience.isBlank()) {
      // No audience filter = broadcast to all
      return Set.of();
    }

    if (eventId == null || eventId.isBlank()) {
      throw new IllegalArgumentException("eventId required when audience is specified");
    }

    Set<String> userIds = new LinkedHashSet<>();
    String[] groups = audience.toLowerCase().split(",");

    for (String group : groups) {
      String normalized = group.trim();
      switch (normalized) {
        case "cfp" -> userIds.addAll(getCfpParticipants(eventId));
        case "cfv" -> userIds.addAll(getVolunteers(eventId));
        case "staff" -> userIds.addAll(getStaff(eventId));
      }
    }

    return userIds;
  }

  /**
   * Get user IDs of all accepted CFP participants (speakers) for an event.
   */
  private Set<String> getCfpParticipants(String eventId) {
    Set<String> userIds = new LinkedHashSet<>();

    cfpSubmissionService
        .listByEventAll(eventId, Optional.of(CfpSubmissionStatus.ACCEPTED), CfpSubmissionService.SortOrder.CREATED_DESC)
        .forEach(
            submission -> {
              // Add the proposer
              if (submission.proposerUserId() != null) {
                userIds.add(sanitizeUserId(submission.proposerUserId()));
              }
              // Add panelists (for panel format talks)
              if (submission.panelists() != null) {
                submission.panelists().stream()
                    .filter(p -> p.userId() != null)
                    .forEach(p -> userIds.add(sanitizeUserId(p.userId())));
              }
            });

    return userIds;
  }

  /**
   * Get user IDs of all selected volunteers for an event.
   */
  private Set<String> getVolunteers(String eventId) {
    Set<String> userIds = new LinkedHashSet<>();

    volunteerApplicationService
        .listByEvent(
            eventId,
            Optional.of(VolunteerApplicationStatus.SELECTED),
            VolunteerApplicationService.SortOrder.CREATED_DESC,
            1000,
            0)
        .forEach(
            app -> {
              if (app.applicantUserId() != null) {
                userIds.add(sanitizeUserId(app.applicantUserId()));
              }
            });

    return userIds;
  }

  /**
   * Get user IDs of all active staff for an event.
   */
  private Set<String> getStaff(String eventId) {
    Set<String> userIds = new LinkedHashSet<>();

    eventOperationsService
        .listStaff(eventId, false) // only active staff
        .forEach(
            staff -> {
              if (staff.userId() != null) {
                userIds.add(sanitizeUserId(staff.userId()));
              }
            });

    return userIds;
  }

  /**
   * Get estimated audience size for preview purposes.
   */
  public AudienceEstimate estimateAudience(String audience, String eventId) {
    if (audience == null || audience.isBlank()) {
      return new AudienceEstimate(true, 0, 0, 0, 0);
    }

    if (eventId == null || eventId.isBlank()) {
      return new AudienceEstimate(false, 0, 0, 0, 0);
    }

    Set<String> userIds = resolveAudience(audience, eventId);

    String[] groups = audience.toLowerCase().split(",");
    int cfpCount = 0;
    int cfvCount = 0;
    int staffCount = 0;

    for (String group : groups) {
      String normalized = group.trim();
      switch (normalized) {
        case "cfp" -> cfpCount = getCfpParticipants(eventId).size();
        case "cfv" -> cfvCount = getVolunteers(eventId).size();
        case "staff" -> staffCount = getStaff(eventId).size();
      }
    }

    return new AudienceEstimate(false, userIds.size(), cfpCount, cfvCount, staffCount);
  }

  private static String sanitizeUserId(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
    return value.isBlank() ? null : value;
  }

  public record AudienceEstimate(
      boolean global, int total, int cfpCount, int cfvCount, int staffCount) {}
}
