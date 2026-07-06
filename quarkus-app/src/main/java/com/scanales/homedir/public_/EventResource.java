package com.scanales.homedir.public_;

import com.scanales.homedir.agenda.AgendaProposalConfigService;
import com.scanales.homedir.cfp.CfpConfigService;
import com.scanales.homedir.cfp.CfpEventConfigService;
import com.scanales.homedir.cfp.CfpFormCatalog;
import com.scanales.homedir.cfp.CfpFormOptionsService;
import com.scanales.homedir.cfp.CfpSubmission;
import com.scanales.homedir.cfp.CfpSubmissionService;
import com.scanales.homedir.cfp.CfpSubmissionStatus;
import com.scanales.homedir.cfp.CfpTimelinePlanner;
import com.scanales.homedir.cfp.CfpTimelineView;
import com.scanales.homedir.eventops.EventOperationsService;
import com.scanales.homedir.eventops.EventStaffRole;
import com.scanales.homedir.model.Event;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.GamificationService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.service.UserSessionService;
import com.scanales.homedir.util.AdminUtils;
import com.scanales.homedir.util.TemplateLocaleUtil;
import com.scanales.homedir.volunteers.VolunteerApplication;
import com.scanales.homedir.volunteers.VolunteerApplicationService;
import com.scanales.homedir.volunteers.VolunteerApplicationStatus;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Path("/event")
public class EventResource {

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance detail(Event event, boolean agendaProposalNoticeEnabled);

    static native TemplateInstance cfp(
        Event event,
        CfpFormCatalog cfpCatalog,
        Map<String, Integer> cfpDurationByFormat,
        CfpTimelineView cfpTimeline);

    static native TemplateInstance cfpSelected(
        Event event, List<CfpSubmission> selectedSubmissions);

    static native TemplateInstance volunteers(Event event, boolean volunteerSelected);

    static native TemplateInstance volunteersLounge(
        Event event, boolean loungeAccess, boolean eventAdmin, String loungeAccessReason);

    static native TemplateInstance volunteersSelected(
        Event event, List<VolunteerApplication> selectedVolunteers);
  }

  @Inject EventService eventService;

  @Inject UsageMetricsService metrics;

  @Inject SecurityIdentity identity;

  @Inject UserSessionService sessionService;

  @Inject CfpFormOptionsService cfpFormOptionsService;

  @Inject CfpConfigService cfpConfigService;
  @Inject CfpEventConfigService cfpEventConfigService;
  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject AgendaProposalConfigService agendaProposalConfigService;
  @Inject GamificationService gamificationService;
  @Inject VolunteerApplicationService volunteerApplicationService;
  @Inject EventOperationsService eventOperationsService;

  @GET
  @Path("{id}")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance event(
      @PathParam("id") String id,
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    String ua = headers.getHeaderString("User-Agent");
    String sessionId = context.session() != null ? context.session().id() : null;
    metrics.recordPageView("/event", sessionId, ua);
    metrics.recordEventView(id, sessionId, ua);
    currentUserId()
        .ifPresent(
            userId -> {
              gamificationService.award(userId, GamificationActivity.EVENT_VIEW, id);
              gamificationService.award(
                  userId, GamificationActivity.WARRIOR_EVENTS_EXPLORATION, "events");
            });
    Event event = eventService.getEvent(id);
    return withLayoutData(
        Templates.detail(
            event,
            agendaProposalConfigService != null
                && agendaProposalConfigService.isProposalNoticeEnabled()),
        "eventos",
        localeCookie,
        headers);
  }

  @GET
  @Path("{id}/cfp")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance cfp(
      @PathParam("id") String id,
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @QueryParam("preview_user_id") String previewUserId,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/event/cfp", headers, context);
    boolean previewMode =
        previewUserId != null
            && !previewUserId.isBlank()
            && AdminUtils.canViewAdminBackoffice(identity);
    if (!previewMode) {
      currentUserId()
          .ifPresent(
              userId ->
                  gamificationService.award(userId, GamificationActivity.AGENDA_VIEW, id + ":cfp"));
    }
    Event event = eventService.getEvent(id);
    CfpTimelineView cfpTimeline = null;
    if (event != null) {
      try {
        CfpEventConfigService.ResolvedEventConfig resolved =
            cfpEventConfigService.resolveForEvent(id);
        cfpTimeline =
            CfpTimelinePlanner.build(
                    event,
                    resolved.opensAt(),
                    resolved.closesAt(),
                    java.util.Locale.forLanguageTag(
                        TemplateLocaleUtil.resolve(localeCookie, headers)))
                .orElse(null);
      } catch (Exception ignored) {
        cfpTimeline = null;
      }
    }
    String normalizedPreviewUserId =
        previewMode ? previewUserId.trim().toLowerCase(java.util.Locale.ROOT) : null;
    return withLayoutData(
            Templates.cfp(
                event,
                cfpFormOptionsService.catalog(),
                cfpFormOptionsService.durationByFormat(),
                cfpTimeline),
            "eventos",
            localeCookie,
            headers)
        .data(
            "cfpTestingModeEnabled",
            cfpConfigService != null && cfpConfigService.isTestingModeEnabled())
        .data("cfpPreviewMode", previewMode)
        .data("cfpPreviewUserId", normalizedPreviewUserId);
  }

  @GET
  @Path("{id}/cfp/speakers")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance cfpSpeakers(
      @PathParam("id") String id,
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/event/cfp/speakers", headers, context);
    currentUserId()
        .ifPresent(
            userId ->
                gamificationService.award(
                    userId, GamificationActivity.EVENT_VIEW, id + ":speakers"));
    Event event = eventService.getEvent(id);
    List<CfpSubmission> acceptedSubmissions = List.of();
    if (event != null && cfpSubmissionService != null) {
      acceptedSubmissions =
          cfpSubmissionService
              .listByEventAll(
                  id,
                  Optional.of(CfpSubmissionStatus.ACCEPTED),
                  CfpSubmissionService.SortOrder.CREATED_DESC)
              .stream()
              .filter(
                  item -> cfpSubmissionService.visibleStatus(item) == CfpSubmissionStatus.ACCEPTED)
              .toList();
    }
    return withLayoutData(
        Templates.cfpSelected(event, acceptedSubmissions), "eventos", localeCookie, headers);
  }

  @GET
  @Path("{id}/cfp/selected")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance cfpSelected(
      @PathParam("id") String id,
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/event/cfp/selected", headers, context);
    currentUserId()
        .ifPresent(
            userId ->
                gamificationService.award(
                    userId, GamificationActivity.EVENT_VIEW, id + ":cfp-selected"));
    Event event = eventService.getEvent(id);
    List<CfpSubmission> selectedSubmissions = List.of();
    if (event != null && cfpSubmissionService != null) {
      selectedSubmissions =
          cfpSubmissionService
              .listByEventAll(
                  id,
                  Optional.of(CfpSubmissionStatus.ACCEPTED),
                  CfpSubmissionService.SortOrder.UPDATED_DESC)
              .stream()
              .filter(
                  item -> cfpSubmissionService.visibleStatus(item) == CfpSubmissionStatus.ACCEPTED)
              .toList();
    }
    return withLayoutData(
        Templates.cfpSelected(event, selectedSubmissions), "eventos", localeCookie, headers);
  }

  @GET
  @Path("{id}/volunteers")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance volunteers(
      @PathParam("id") String id,
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/event/volunteers", headers, context);
    currentUserId()
        .ifPresent(
            userId -> gamificationService.award(userId, GamificationActivity.VOLUNTEER_VIEW, id));
    Event event = eventService.getEvent(id);
    boolean volunteerSelected = false;
    java.util.Optional<String> currentUser = currentUserId();
    if (event != null && currentUser.isPresent()) {
      java.util.Optional<VolunteerApplication> app =
          volunteerApplicationService.findByEventAndUser(id, currentUser.get());
      volunteerSelected =
          app.isPresent() && app.get().status() == VolunteerApplicationStatus.SELECTED;
    }
    return withLayoutData(
        Templates.volunteers(event, volunteerSelected), "eventos", localeCookie, headers);
  }

  @GET
  @Path("{id}/volunteers/selected")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance volunteersSelected(
      @PathParam("id") String id,
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/event/volunteers/selected", headers, context);
    currentUserId()
        .ifPresent(
            userId ->
                gamificationService.award(
                    userId, GamificationActivity.VOLUNTEER_VIEW, id + ":selected"));
    Event event = eventService.getEvent(id);
    List<VolunteerApplication> selectedVolunteers = List.of();
    if (event != null && volunteerApplicationService != null) {
      selectedVolunteers =
          volunteerApplicationService.listByEvent(
              id,
              Optional.of(VolunteerApplicationStatus.SELECTED),
              VolunteerApplicationService.SortOrder.CREATED_DESC,
              Integer.MAX_VALUE,
              0);
    }
    return withLayoutData(
        Templates.volunteersSelected(event, selectedVolunteers), "eventos", localeCookie, headers);
  }

  @GET
  @Path("{id}/volunteers/lounge")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance volunteersLounge(
      @PathParam("id") String id,
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/event/volunteers/lounge", headers, context);
    currentUserId()
        .ifPresent(
            userId -> gamificationService.award(userId, GamificationActivity.VOLUNTEER_VIEW, id));
    Event event = eventService.getEvent(id);
    boolean authenticated = identity != null && !identity.isAnonymous();
    boolean eventAdmin = authenticated && AdminUtils.isAdmin(identity);
    boolean loungeAccess = eventAdmin;
    String loungeAccessReason = "login_required";
    if (authenticated) {
      loungeAccessReason = "volunteer_access_denied";
      if (!eventAdmin) {
        Optional<String> currentUser = currentUserId();
        if (currentUser.isPresent()) {
          java.util.Set<String> aliases = java.util.Set.of(currentUser.get());
          boolean hasStaffAccess =
              eventOperationsService.hasStaffRole(
                  id,
                  aliases,
                  java.util.Set.of(
                      EventStaffRole.ORGANIZER,
                      EventStaffRole.PRODUCTION,
                      EventStaffRole.OPERATIONS,
                      EventStaffRole.VOLUNTEER),
                  true);
          Optional<VolunteerApplication> app =
              volunteerApplicationService.findByEventAndUser(id, currentUser.get());
          if (hasStaffAccess
              || (app.isPresent() && app.get().status() == VolunteerApplicationStatus.SELECTED)) {
            loungeAccess = true;
            loungeAccessReason = "selected";
          }
        }
      } else {
        loungeAccessReason = "admin";
      }
    }
    return withLayoutData(
            Templates.volunteersLounge(event, loungeAccess, eventAdmin, loungeAccessReason),
            "eventos",
            localeCookie,
            headers)
        .data("loungeAccess", loungeAccess)
        .data("eventAdmin", eventAdmin)
        .data("loungeAccessReason", loungeAccessReason);
  }

  private TemplateInstance withLayoutData(
      TemplateInstance templateInstance,
      String activePage,
      String localeCookie,
      jakarta.ws.rs.core.HttpHeaders headers) {
    boolean authenticated = identity != null && !identity.isAnonymous();
    String userName = authenticated ? identity.getPrincipal().getName() : null;
    return TemplateLocaleUtil.apply(templateInstance, localeCookie, headers)
        .data("activePage", activePage)
        .data("userAuthenticated", authenticated)
        .data("userName", userName)
        .data("userSession", sessionService.getCurrentSession())
        .data("userInitial", initialFrom(userName));
  }

  private String initialFrom(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.substring(0, 1).toUpperCase();
  }

  private java.util.Optional<String> currentUserId() {
    if (identity == null || identity.isAnonymous()) {
      return java.util.Optional.empty();
    }
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return java.util.Optional.of(email.toLowerCase());
    }
    String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    if (principal != null && !principal.isBlank()) {
      return java.util.Optional.of(principal.toLowerCase());
    }
    return java.util.Optional.empty();
  }
}
