package com.scanales.eventflow.public_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.notifications.Notification;
import com.scanales.eventflow.notifications.NotificationService;
import com.scanales.eventflow.notifications.NotificationType;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.util.AdminUtils;
import com.scanales.eventflow.util.PaginationGuardrails;
import com.scanales.eventflow.volunteers.VolunteerApplication;
import com.scanales.eventflow.volunteers.VolunteerApplicationService;
import com.scanales.eventflow.volunteers.VolunteerApplicationStatus;
import com.scanales.eventflow.volunteers.VolunteerEventConfig;
import com.scanales.eventflow.volunteers.VolunteerEventConfigService;
import com.scanales.eventflow.volunteers.VolunteerInsightsService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Path("/api/events/{eventId}/volunteers/submissions")
@Produces(MediaType.APPLICATION_JSON)
public class VolunteerSubmissionApiResource {

  private static final int DEFAULT_LIMIT = PaginationGuardrails.DEFAULT_PAGE_LIMIT;
  private static final int MAX_LIMIT = PaginationGuardrails.MAX_PAGE_LIMIT;
  private static final int MAX_OFFSET = PaginationGuardrails.MAX_OFFSET;

  @Inject VolunteerApplicationService volunteerApplicationService;
  @Inject VolunteerEventConfigService volunteerEventConfigService;
  @Inject UsageMetricsService metrics;
  @Inject GamificationService gamificationService;
  @Inject EventService eventService;
  @Inject NotificationService notificationService;
  @Inject VolunteerInsightsService volunteerInsightsService;
  @Inject SecurityIdentity identity;

  @POST
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response create(@PathParam("eventId") String eventId, CreateVolunteerRequest request) {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(Map.of("error", "user_not_authenticated"))
          .build();
    }
    String primaryUserId = userIds.iterator().next();
    try {
      VolunteerApplication created =
          volunteerApplicationService.create(
              primaryUserId,
              currentUserName().orElse(primaryUserId),
              new VolunteerApplicationService.CreateRequest(
                  eventId,
                  request != null ? request.aboutMe() : null,
                  request != null ? request.joinReason() : null,
                  request != null ? request.differentiator() : null));
      metrics.recordFunnelStep("volunteer_submit");
      metrics.recordFunnelStep("volunteer.submission.create");
      gamificationService.award(primaryUserId, GamificationActivity.VOLUNTEER_APPLY, eventId);
      volunteerInsightsService.recordApplicationSubmitted(created);
      return Response.status(Response.Status.CREATED)
          .entity(new VolunteerSubmissionResponse(toView(created)))
          .build();
    } catch (VolunteerApplicationService.ValidationException e) {
      Response.Status status =
          ("already_applied".equals(e.getMessage()) || "submissions_closed".equals(e.getMessage()))
              ? Response.Status.CONFLICT
              : Response.Status.BAD_REQUEST;
      return Response.status(status).entity(Map.of("error", e.getMessage())).build();
    } catch (VolunteerApplicationService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @PUT
  @Path("/{id}")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateMine(
      @PathParam("eventId") String eventId,
      @PathParam("id") String id,
      UpdateVolunteerRequest request) {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(Map.of("error", "user_not_authenticated"))
          .build();
    }
    String primaryUserId = userIds.iterator().next();
    try {
      VolunteerApplication updated =
          volunteerApplicationService.updateMine(
              id,
              eventId,
              primaryUserId,
              new VolunteerApplicationService.UpdateRequest(
                  request != null ? request.aboutMe() : null,
                  request != null ? request.joinReason() : null,
                  request != null ? request.differentiator() : null));
      volunteerInsightsService.recordApplicationUpdated(updated);
      return Response.ok(new VolunteerSubmissionResponse(toView(updated))).build();
    } catch (VolunteerApplicationService.ValidationException
        | VolunteerApplicationService.InvalidTransitionException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (VolunteerApplicationService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @POST
  @Path("/{id}/withdraw")
  @Authenticated
  public Response withdrawMine(@PathParam("eventId") String eventId, @PathParam("id") String id) {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(Map.of("error", "user_not_authenticated"))
          .build();
    }
    String primaryUserId = userIds.iterator().next();
    try {
      VolunteerApplication updated = volunteerApplicationService.withdrawMine(id, eventId, primaryUserId);
      metrics.recordFunnelStep("volunteer.submission.withdraw");
      gamificationService.award(primaryUserId, GamificationActivity.VOLUNTEER_WITHDRAW, eventId);
      volunteerInsightsService.recordApplicationWithdrawn(updated);
      return Response.ok(new VolunteerSubmissionResponse(toView(updated))).build();
    } catch (VolunteerApplicationService.ValidationException
        | VolunteerApplicationService.InvalidTransitionException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (VolunteerApplicationService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @GET
  @Path("/mine")
  @Authenticated
  public Response mine(
      @PathParam("eventId") String eventId,
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(Map.of("error", "user_not_authenticated"))
          .build();
    }
    int limit = normalizeLimit(limitParam);
    int offset = PaginationGuardrails.clampOffset(offsetParam, MAX_OFFSET);
    List<VolunteerView> items = findMineForAliases(eventId, userIds).stream().map(this::toView).toList();
    int total = items.size();
    boolean hasMore = false;
    Integer nextOffset = null;
    return Response.ok(new VolunteerSubmissionListResponse(limit, offset, total, hasMore, nextOffset, items))
        .build();
  }

  private List<VolunteerApplication> findMineForAliases(String eventId, Set<String> aliases) {
    if (aliases == null || aliases.isEmpty()) {
      return List.of();
    }
    for (String alias : aliases) {
      Optional<VolunteerApplication> found = volunteerApplicationService.findByEventAndUser(eventId, alias);
      if (found.isPresent()) {
        return List.of(found.get());
      }
    }
    return List.of();
  }

  @GET
  @Path("/config")
  public Response config(@PathParam("eventId") String eventId) {
    try {
      VolunteerEventConfigService.ResolvedEventConfig resolved =
          volunteerEventConfigService.resolveForEvent(eventId);
      return Response.ok(toPublicConfigView(resolved)).build();
    } catch (VolunteerEventConfigService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @GET
  @Path("/event-config")
  @Authenticated
  public Response eventConfig(@PathParam("eventId") String eventId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    try {
      VolunteerEventConfigService.ResolvedEventConfig resolved =
          volunteerEventConfigService.resolveForEvent(eventId);
      Optional<VolunteerEventConfig> override = volunteerEventConfigService.findOverride(eventId);
      return Response.ok(
              new EventVolunteerConfigResponse(
                  resolved.eventId(),
                  resolved.hasOverride(),
                  override.map(this::toConfigView).orElse(null),
                  toConfigView(resolved)))
          .build();
    } catch (VolunteerEventConfigService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @PUT
  @Path("/event-config")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateEventConfig(
      @PathParam("eventId") String eventId, EventVolunteerConfigUpdateRequest request) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    if (request == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_config")).build();
    }
    try {
      VolunteerEventConfig override =
          volunteerEventConfigService.upsert(
              eventId,
              new VolunteerEventConfigService.UpdateRequest(
                  request.acceptingSubmissions(), request.opensAt(), request.closesAt()));
      VolunteerEventConfigService.ResolvedEventConfig resolved =
          volunteerEventConfigService.resolveForEvent(eventId);
      return Response.ok(
              new EventVolunteerConfigResponse(
                  resolved.eventId(), true, toConfigView(override), toConfigView(resolved)))
          .build();
    } catch (VolunteerEventConfigService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @DELETE
  @Path("/event-config")
  @Authenticated
  public Response clearEventConfig(@PathParam("eventId") String eventId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    try {
      boolean cleared = volunteerEventConfigService.clearOverride(eventId);
      VolunteerEventConfigService.ResolvedEventConfig resolved =
          volunteerEventConfigService.resolveForEvent(eventId);
      return Response.ok(
              new EventVolunteerConfigClearResponse(
                  resolved.eventId(), cleared, toConfigView(resolved)))
          .build();
    } catch (VolunteerEventConfigService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @GET
  @Authenticated
  public Response listForModeration(
      @PathParam("eventId") String eventId,
      @QueryParam("status") String status,
      @QueryParam("sort") String sort,
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    Optional<VolunteerApplicationStatus> statusFilter = parseStatusFilter(status);
    if (statusFilter == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_status")).build();
    }
    VolunteerApplicationService.SortOrder sortOrder = parseSortOrder(sort);
    if (sortOrder == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_sort")).build();
    }
    int limit = normalizeLimit(limitParam);
    int offset = PaginationGuardrails.clampOffset(offsetParam, MAX_OFFSET);

    List<VolunteerView> items =
        volunteerApplicationService.listByEvent(eventId, statusFilter, sortOrder, limit, offset).stream()
            .map(this::toView)
            .toList();
    int total = volunteerApplicationService.countByEvent(eventId, statusFilter);
    boolean hasMore = offset + items.size() < total;
    Integer nextOffset = hasMore ? offset + items.size() : null;
    return Response.ok(new VolunteerSubmissionListResponse(limit, offset, total, hasMore, nextOffset, items))
        .build();
  }

  @PUT
  @Path("/{id}/status")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateStatus(
      @PathParam("eventId") String eventId, @PathParam("id") String id, UpdateStatusRequest request) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    VolunteerApplicationStatus newStatus =
        request != null ? VolunteerApplicationStatus.fromApi(request.status()).orElse(null) : null;
    if (newStatus == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_status")).build();
    }
    try {
      Optional<VolunteerApplication> beforeUpdate = volunteerApplicationService.findById(id);
      VolunteerApplication updated =
          volunteerApplicationService.updateStatus(
              id,
              newStatus,
              currentModeratorName().orElse("admin"),
              request != null ? request.note() : null,
              request != null ? request.expectedUpdatedAt() : null);
      if (!eventId.equalsIgnoreCase(updated.eventId())) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(Map.of("error", "application_not_found"))
            .build();
      }
      metrics.recordFunnelStep("volunteer.submission.status");
      metrics.recordFunnelStep("volunteer.submission.status." + updated.status().apiValue());
      if (updated.status() == VolunteerApplicationStatus.SELECTED) {
        metrics.recordFunnelStep("volunteer_selected");
        gamificationService.award(
            updated.applicantUserId(), GamificationActivity.VOLUNTEER_SELECTED, updated.eventId());
      }
      volunteerInsightsService.recordStatusChange(beforeUpdate.orElse(null), updated);
      notifyApplicantStatusChange(beforeUpdate.orElse(null), updated);
      return Response.ok(new VolunteerSubmissionResponse(toView(updated))).build();
    } catch (VolunteerApplicationService.ValidationException
        | VolunteerApplicationService.InvalidTransitionException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (VolunteerApplicationService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @PUT
  @Path("/{id}/rating")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateRating(
      @PathParam("eventId") String eventId, @PathParam("id") String id, UpdateRatingRequest request) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    if (request == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_rating")).build();
    }
    try {
      VolunteerApplication updated =
          volunteerApplicationService.updateRating(
              eventId,
              id,
              request.profile(),
              request.motivation(),
              request.differentiator(),
              currentModeratorName().orElse("admin"),
              request.expectedUpdatedAt());
      volunteerInsightsService.recordRatingUpdated(updated);
      return Response.ok(new VolunteerSubmissionResponse(toView(updated))).build();
    } catch (VolunteerApplicationService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (VolunteerApplicationService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @GET
  @Path("/stats")
  @Authenticated
  public Response stats(@PathParam("eventId") String eventId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    VolunteerApplicationService.EventStats stats = volunteerApplicationService.statsByEvent(eventId);
    return Response.ok(
            new VolunteerStatsResponse(
                stats.total(),
                stats.countsByStatus().getOrDefault(VolunteerApplicationStatus.APPLIED, 0),
                stats.countsByStatus().getOrDefault(VolunteerApplicationStatus.UNDER_REVIEW, 0),
                stats.countsByStatus().getOrDefault(VolunteerApplicationStatus.SELECTED, 0),
                stats.countsByStatus().getOrDefault(VolunteerApplicationStatus.NOT_SELECTED, 0),
                stats.countsByStatus().getOrDefault(VolunteerApplicationStatus.WITHDRAWN, 0),
                stats.latestUpdatedAt()))
        .build();
  }

  private VolunteerConfigPublicView toPublicConfigView(
      VolunteerEventConfigService.ResolvedEventConfig config) {
    return new VolunteerConfigPublicView(
        config.acceptingSubmissions(),
        config.opensAt(),
        config.closesAt(),
        config.currentlyOpen(),
        config.hasOverride());
  }

  private EventVolunteerConfigView toConfigView(VolunteerEventConfig config) {
    return new EventVolunteerConfigView(
        config.acceptingSubmissions(),
        config.opensAt(),
        config.closesAt(),
        null);
  }

  private EventVolunteerConfigView toConfigView(VolunteerEventConfigService.ResolvedEventConfig config) {
    return new EventVolunteerConfigView(
        config.acceptingSubmissions(),
        config.opensAt(),
        config.closesAt(),
        config.currentlyOpen());
  }

  private VolunteerView toView(VolunteerApplication item) {
    return new VolunteerView(
        item.id(),
        item.eventId(),
        item.applicantUserId(),
        item.applicantName(),
        item.aboutMe(),
        item.joinReason(),
        item.differentiator(),
        item.status() != null ? item.status().apiValue() : VolunteerApplicationStatus.APPLIED.apiValue(),
        item.createdAt(),
        item.updatedAt(),
        item.moderatedAt(),
        item.moderatedBy(),
        item.moderationNote(),
        item.ratingProfile(),
        item.ratingMotivation(),
        item.ratingDifferentiator(),
        VolunteerApplicationService.calculateWeightedScore(item));
  }

  private Set<String> currentUserIds() {
    if (identity == null || identity.isAnonymous()) {
      return Set.of();
    }
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    addNormalizedUserId(ids, principal);
    addNormalizedUserId(ids, AdminUtils.getClaim(identity, "email"));
    addNormalizedUserId(ids, AdminUtils.getClaim(identity, "sub"));
    return ids.isEmpty() ? Set.of() : Collections.unmodifiableSet(ids);
  }

  private Optional<String> currentUserName() {
    if (identity == null || identity.isAnonymous()) {
      return Optional.empty();
    }
    String name = AdminUtils.getClaim(identity, "name");
    if (name != null && !name.isBlank()) {
      return Optional.of(name);
    }
    String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    if (principal == null || principal.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(principal);
  }

  private Optional<String> currentModeratorName() {
    Optional<String> name = currentUserName();
    if (name.isPresent()) {
      return name;
    }
    Set<String> ids = currentUserIds();
    if (ids.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(ids.iterator().next());
  }

  private static void addNormalizedUserId(Set<String> target, String raw) {
    if (raw == null || raw.isBlank()) {
      return;
    }
    target.add(raw.trim().toLowerCase(Locale.ROOT));
  }

  private static Optional<VolunteerApplicationStatus> parseStatusFilter(String status) {
    if (status == null || status.isBlank()) {
      return Optional.of(VolunteerApplicationStatus.APPLIED);
    }
    if ("all".equalsIgnoreCase(status.trim())) {
      return Optional.empty();
    }
    Optional<VolunteerApplicationStatus> parsed = VolunteerApplicationStatus.fromApi(status);
    return parsed.isPresent() ? parsed : null;
  }

  private static VolunteerApplicationService.SortOrder parseSortOrder(String sort) {
    if (sort == null || sort.isBlank() || "created".equalsIgnoreCase(sort) || "recent".equalsIgnoreCase(sort)) {
      return VolunteerApplicationService.SortOrder.CREATED_DESC;
    }
    if ("updated".equalsIgnoreCase(sort) || "latest_update".equalsIgnoreCase(sort)) {
      return VolunteerApplicationService.SortOrder.UPDATED_DESC;
    }
    if ("score".equalsIgnoreCase(sort) || "weighted".equalsIgnoreCase(sort)) {
      return VolunteerApplicationService.SortOrder.SCORE_DESC;
    }
    return null;
  }

  private static int normalizeLimit(Integer rawLimit) {
    return PaginationGuardrails.clampLimit(rawLimit, DEFAULT_LIMIT, MAX_LIMIT);
  }

  private void notifyApplicantStatusChange(VolunteerApplication before, VolunteerApplication after) {
    if (after == null || after.status() == null) {
      return;
    }
    VolunteerApplicationStatus previousStatus = before != null ? before.status() : null;
    VolunteerApplicationStatus nextStatus = after.status();
    if (previousStatus == nextStatus) {
      return;
    }
    String targetUser = after.applicantUserId();
    if (targetUser == null || targetUser.isBlank()) {
      return;
    }
    Notification notification = new Notification();
    notification.id = UUID.randomUUID().toString();
    notification.userId = targetUser;
    notification.eventId = after.eventId();
    notification.type = NotificationType.SOCIAL;
    notification.title = "Volunteer application update";
    notification.message =
        "Your volunteer application for "
            + resolveEventTitle(after.eventId())
            + " is now "
            + humanStatus(nextStatus)
            + ".";
    long stamp = after.updatedAt() != null ? after.updatedAt().toEpochMilli() : System.currentTimeMillis();
    notification.dedupeKey =
        "volunteer-status:" + after.id() + ":" + nextStatus.apiValue() + ":" + stamp;
    notificationService.enqueue(notification);
  }

  private String resolveEventTitle(String eventId) {
    Event event = eventService != null ? eventService.getEvent(eventId) : null;
    if (event != null && event.getTitle() != null && !event.getTitle().isBlank()) {
      return event.getTitle().trim();
    }
    if (eventId == null || eventId.isBlank()) {
      return "this event";
    }
    return eventId;
  }

  private static String humanStatus(VolunteerApplicationStatus status) {
    if (status == null) {
      return "updated";
    }
    return switch (status) {
      case APPLIED -> "applied";
      case UNDER_REVIEW -> "under review";
      case SELECTED -> "selected";
      case NOT_SELECTED -> "not selected";
      case WITHDRAWN -> "withdrawn";
    };
  }

  public record CreateVolunteerRequest(
      @JsonProperty("about_me") String aboutMe,
      @JsonProperty("join_reason") String joinReason,
      String differentiator) {}

  public record UpdateVolunteerRequest(
      @JsonProperty("about_me") String aboutMe,
      @JsonProperty("join_reason") String joinReason,
      String differentiator) {}

  public record UpdateStatusRequest(
      String status,
      String note,
      @JsonProperty("expected_updated_at") Instant expectedUpdatedAt) {}

  public record UpdateRatingRequest(
      Integer profile,
      Integer motivation,
      Integer differentiator,
      @JsonProperty("expected_updated_at") Instant expectedUpdatedAt) {}

  public record EventVolunteerConfigUpdateRequest(
      @JsonProperty("accepting_submissions") Boolean acceptingSubmissions,
      @JsonProperty("opens_at") Instant opensAt,
      @JsonProperty("closes_at") Instant closesAt) {}

  public record VolunteerSubmissionResponse(VolunteerView item) {}

  public record VolunteerSubmissionListResponse(
      int limit,
      int offset,
      int total,
      @JsonProperty("has_more") boolean hasMore,
      @JsonProperty("next_offset") Integer nextOffset,
      List<VolunteerView> items) {}

  public record VolunteerStatsResponse(
      int total,
      int applied,
      @JsonProperty("under_review") int underReview,
      int selected,
      @JsonProperty("not_selected") int notSelected,
      int withdrawn,
      @JsonProperty("latest_updated_at") Instant latestUpdatedAt) {}

  public record VolunteerConfigPublicView(
      @JsonProperty("accepting_submissions") boolean acceptingSubmissions,
      @JsonProperty("opens_at") Instant opensAt,
      @JsonProperty("closes_at") Instant closesAt,
      @JsonProperty("currently_open") boolean currentlyOpen,
      @JsonProperty("has_override") boolean hasOverride) {}

  public record EventVolunteerConfigView(
      @JsonProperty("accepting_submissions") boolean acceptingSubmissions,
      @JsonProperty("opens_at") Instant opensAt,
      @JsonProperty("closes_at") Instant closesAt,
      @JsonProperty("currently_open") Boolean currentlyOpen) {}

  public record EventVolunteerConfigResponse(
      @JsonProperty("event_id") String eventId,
      @JsonProperty("has_override") boolean hasOverride,
      EventVolunteerConfigView override,
      EventVolunteerConfigView resolved) {}

  public record EventVolunteerConfigClearResponse(
      @JsonProperty("event_id") String eventId,
      boolean cleared,
      EventVolunteerConfigView resolved) {}

  public record VolunteerView(
      String id,
      @JsonProperty("event_id") String eventId,
      @JsonProperty("applicant_user_id") String applicantUserId,
      @JsonProperty("applicant_name") String applicantName,
      @JsonProperty("about_me") String aboutMe,
      @JsonProperty("join_reason") String joinReason,
      String differentiator,
      String status,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("updated_at") Instant updatedAt,
      @JsonProperty("moderated_at") Instant moderatedAt,
      @JsonProperty("moderated_by") String moderatedBy,
      @JsonProperty("moderation_note") String moderationNote,
      @JsonProperty("rating_profile") Integer ratingProfile,
      @JsonProperty("rating_motivation") Integer ratingMotivation,
      @JsonProperty("rating_differentiator") Integer ratingDifferentiator,
      @JsonProperty("rating_weighted") Double ratingWeighted) {}
}
