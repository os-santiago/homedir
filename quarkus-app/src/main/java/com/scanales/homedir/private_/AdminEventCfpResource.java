package com.scanales.homedir.private_;

import com.scanales.homedir.cfp.CfpSubmissionService;
import com.scanales.homedir.eventops.EventOperationsService;
import com.scanales.homedir.eventops.EventStaffRole;
import com.scanales.homedir.model.Event;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.util.AdminUtils;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Path("/private/admin/events/{id}/cfp")
public class AdminEventCfpResource {
  private static final Set<EventStaffRole> CFP_REVIEWER_ROLES = Set.of(EventStaffRole.CFP_REVIEWER);

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance moderation(Event event, boolean canManage);

    static native TemplateInstance detail(Event event, String submissionId, boolean canManage);
  }

  @Inject EventService eventService;

  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject EventOperationsService eventOperationsService;

  @Inject SecurityIdentity identity;

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response moderation(@PathParam("id") String eventId) {
    if (!canReviewCfp(eventId)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    Event event = eventService.getEvent(eventId);
    if (event == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(Templates.moderation(event, AdminUtils.canManageAdminBackoffice(identity))).build();
  }

  @GET
  @Path("/submissions/{submissionId}")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response submissionDetail(
      @PathParam("id") String eventId, @PathParam("submissionId") String submissionId) {
    if (!canReviewCfp(eventId)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    Event event = eventService.getEvent(eventId);
    if (event == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    boolean existsForEvent =
        cfpSubmissionService.findById(submissionId).filter(item -> eventId.equals(item.eventId())).isPresent();
    if (!existsForEvent) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(Templates.detail(event, submissionId, AdminUtils.canManageAdminBackoffice(identity))).build();
  }

  private boolean canReviewCfp(String eventId) {
    if (AdminUtils.canViewAdminBackoffice(identity)) {
      return true;
    }
    return eventOperationsService.hasStaffRole(eventId, currentUserIds(), CFP_REVIEWER_ROLES, true);
  }

  private Set<String> currentUserIds() {
    if (identity == null || identity.isAnonymous()) {
      return Set.of();
    }
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    addNormalizedUserId(ids, identity.getPrincipal() != null ? identity.getPrincipal().getName() : null);
    addNormalizedUserId(ids, AdminUtils.getClaim(identity, "email"));
    addNormalizedUserId(ids, AdminUtils.getClaim(identity, "sub"));
    return ids.isEmpty() ? Set.of() : Set.copyOf(ids);
  }

  private static void addNormalizedUserId(Set<String> target, String raw) {
    if (raw == null || raw.isBlank()) {
      return;
    }
    target.add(raw.trim().toLowerCase(Locale.ROOT));
  }
}
