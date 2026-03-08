package com.scanales.eventflow.public_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.eventops.EventOperationsService;
import com.scanales.eventflow.eventops.EventStaffRole;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.util.AdminUtils;
import com.scanales.eventflow.util.PaginationGuardrails;
import com.scanales.eventflow.volunteers.VolunteerApplication;
import com.scanales.eventflow.volunteers.VolunteerApplicationService;
import com.scanales.eventflow.volunteers.VolunteerApplicationStatus;
import com.scanales.eventflow.volunteers.VolunteerLoungeMessage;
import com.scanales.eventflow.volunteers.VolunteerLoungeMessageType;
import com.scanales.eventflow.volunteers.VolunteerLoungeService;
import com.scanales.eventflow.volunteers.VolunteerInsightsService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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

@Path("/api/events/{eventId}/volunteers/lounge")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class VolunteerLoungeApiResource {
  private static final int DEFAULT_LIMIT = PaginationGuardrails.DEFAULT_PAGE_LIMIT;
  private static final int MAX_LIMIT = PaginationGuardrails.MAX_PAGE_LIMIT;
  private static final int MAX_OFFSET = PaginationGuardrails.MAX_OFFSET;

  @Inject VolunteerLoungeService volunteerLoungeService;
  @Inject VolunteerApplicationService volunteerApplicationService;
  @Inject EventOperationsService eventOperationsService;
  @Inject SecurityIdentity identity;
  @Inject UsageMetricsService metrics;
  @Inject GamificationService gamificationService;
  @Inject VolunteerInsightsService volunteerInsightsService;

  @GET
  public Response list(
      @PathParam("eventId") String eventId,
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(Map.of("error", "user_not_authenticated"))
          .build();
    }
    if (!hasAccess(eventId, userIds)) {
      return Response.status(Response.Status.FORBIDDEN)
          .entity(Map.of("error", "volunteer_access_denied"))
          .build();
    }
    int limit = PaginationGuardrails.clampLimit(limitParam, DEFAULT_LIMIT, MAX_LIMIT);
    int offset = PaginationGuardrails.clampOffset(offsetParam, MAX_OFFSET);
    List<VolunteerLoungeItem> items =
        volunteerLoungeService.listByEvent(eventId, limit, offset).stream().map(this::toItem).toList();
    int total = volunteerLoungeService.countByEvent(eventId);
    boolean hasMore = offset + items.size() < total;
    Integer nextOffset = hasMore ? offset + items.size() : null;
    return Response.ok(new VolunteerLoungeListResponse(limit, offset, total, hasMore, nextOffset, items)).build();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response create(@PathParam("eventId") String eventId, CreateVolunteerLoungeRequest request) {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(Map.of("error", "user_not_authenticated"))
          .build();
    }
    if (!hasAccess(eventId, userIds)) {
      return Response.status(Response.Status.FORBIDDEN)
          .entity(Map.of("error", "volunteer_access_denied"))
          .build();
    }
    String primaryUserId = userIds.iterator().next();
    try {
      VolunteerLoungeMessage created =
          volunteerLoungeService.create(
              eventId,
              primaryUserId,
              currentUserName().orElse(primaryUserId),
              request != null ? request.body() : null,
              request != null ? request.parentId() : null,
              VolunteerLoungeMessageType.POST);
      metrics.recordFunnelStep("volunteer.lounge.post");
      metrics.recordFunnelStep("volunteer_lounge_post");
      gamificationService.award(primaryUserId, GamificationActivity.VOLUNTEER_LOUNGE_POST, eventId);
      volunteerInsightsService.recordLoungePost(created);
      return Response.status(Response.Status.CREATED)
          .entity(new VolunteerLoungeMutationResponse(toItem(created)))
          .build();
    } catch (VolunteerLoungeService.ValidationException e) {
      if ("rate_limit".equals(e.getMessage())) {
        return Response.status(Response.Status.TOO_MANY_REQUESTS)
            .entity(Map.of("error", e.getMessage()))
            .build();
      }
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", e.getMessage()))
          .build();
    } catch (VolunteerLoungeService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", e.getMessage()))
          .build();
    }
  }

  @GET
  @Path("/announcements")
  public Response listAnnouncements(
      @PathParam("eventId") String eventId,
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(Map.of("error", "user_not_authenticated"))
          .build();
    }
    if (!hasAccess(eventId, userIds)) {
      return Response.status(Response.Status.FORBIDDEN)
          .entity(Map.of("error", "volunteer_access_denied"))
          .build();
    }
    int limit = PaginationGuardrails.clampLimit(limitParam, DEFAULT_LIMIT, MAX_LIMIT);
    int offset = PaginationGuardrails.clampOffset(offsetParam, MAX_OFFSET);
    List<VolunteerLoungeItem> items =
        volunteerLoungeService
            .listByEventAndType(eventId, VolunteerLoungeMessageType.ANNOUNCEMENT, limit, offset)
            .stream()
            .map(this::toItem)
            .toList();
    int total = volunteerLoungeService.countByEventAndType(eventId, VolunteerLoungeMessageType.ANNOUNCEMENT);
    boolean hasMore = offset + items.size() < total;
    Integer nextOffset = hasMore ? offset + items.size() : null;
    return Response.ok(new VolunteerLoungeListResponse(limit, offset, total, hasMore, nextOffset, items)).build();
  }

  @POST
  @Path("/announcements")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createAnnouncement(
      @PathParam("eventId") String eventId, CreateVolunteerLoungeAnnouncementRequest request) {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(Map.of("error", "user_not_authenticated"))
          .build();
    }
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    String primaryUserId = userIds.iterator().next();
    try {
      VolunteerLoungeMessage created =
          volunteerLoungeService.create(
              eventId,
              primaryUserId,
              currentUserName().orElse(primaryUserId),
              request != null ? request.body() : null,
              null,
              VolunteerLoungeMessageType.ANNOUNCEMENT);
      metrics.recordFunnelStep("volunteer.lounge.announcement");
      volunteerInsightsService.recordLoungeAnnouncement(created);
      return Response.status(Response.Status.CREATED)
          .entity(new VolunteerLoungeMutationResponse(toItem(created)))
          .build();
    } catch (VolunteerLoungeService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", e.getMessage()))
          .build();
    } catch (VolunteerLoungeService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", e.getMessage()))
          .build();
    }
  }

  @DELETE
  @Path("/announcements/{id}")
  public Response deleteAnnouncement(@PathParam("eventId") String eventId, @PathParam("id") String id) {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(Map.of("error", "user_not_authenticated"))
          .build();
    }
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    boolean deleted = volunteerLoungeService.delete(eventId, id);
    if (!deleted) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "announcement_not_found")).build();
    }
    metrics.recordFunnelStep("volunteer.lounge.announcement.delete");
    return Response.noContent().build();
  }

  private boolean hasAccess(String eventId, Set<String> userIds) {
    if (AdminUtils.isAdmin(identity)) {
      return true;
    }
    if (userIds == null || userIds.isEmpty()) {
      return false;
    }
    boolean hasStaffAccess =
        eventOperationsService.hasStaffRole(
            eventId,
            userIds,
            Set.of(
                EventStaffRole.ORGANIZER,
                EventStaffRole.PRODUCTION,
                EventStaffRole.OPERATIONS,
                EventStaffRole.VOLUNTEER),
            true);
    if (hasStaffAccess) {
      return true;
    }
    for (String userId : userIds) {
      Optional<VolunteerApplication> app = volunteerApplicationService.findByEventAndUser(eventId, userId);
      if (app.isPresent() && app.get().status() == VolunteerApplicationStatus.SELECTED) {
        return true;
      }
    }
    return false;
  }

  private VolunteerLoungeItem toItem(VolunteerLoungeMessage item) {
    return new VolunteerLoungeItem(
        item.id(),
        item.eventId(),
        item.normalizedMessageType(),
        item.parentId(),
        item.userId(),
        item.userName(),
        item.body(),
        item.createdAt(),
        item.updatedAt());
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

  private static void addNormalizedUserId(Set<String> target, String raw) {
    if (raw == null || raw.isBlank()) {
      return;
    }
    target.add(raw.trim().toLowerCase(Locale.ROOT));
  }

  public record CreateVolunteerLoungeRequest(String body, @JsonProperty("parent_id") String parentId) {}

  public record CreateVolunteerLoungeAnnouncementRequest(String body) {}

  public record VolunteerLoungeItem(
      String id,
      @JsonProperty("event_id") String eventId,
      @JsonProperty("message_type") String messageType,
      @JsonProperty("parent_id") String parentId,
      @JsonProperty("user_id") String userId,
      @JsonProperty("user_name") String userName,
      String body,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("updated_at") Instant updatedAt) {}

  public record VolunteerLoungeListResponse(
      int limit,
      int offset,
      int total,
      @JsonProperty("has_more") boolean hasMore,
      @JsonProperty("next_offset") Integer nextOffset,
      List<VolunteerLoungeItem> items) {}

  public record VolunteerLoungeMutationResponse(VolunteerLoungeItem item) {}
}
