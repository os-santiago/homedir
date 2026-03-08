package com.scanales.eventflow.public_;

import com.scanales.eventflow.agenda.AgendaProposalConfigService;
import com.scanales.eventflow.cfp.CfpFormCatalog;
import com.scanales.eventflow.cfp.CfpConfigService;
import com.scanales.eventflow.cfp.CfpEventConfigService;
import com.scanales.eventflow.cfp.CfpFormOptionsService;
import com.scanales.eventflow.cfp.CfpTimelinePlanner;
import com.scanales.eventflow.cfp.CfpTimelineView;
import com.scanales.eventflow.eventops.EventOperationsService;
import com.scanales.eventflow.eventops.EventStaffRole;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.volunteers.VolunteerApplication;
import com.scanales.eventflow.volunteers.VolunteerApplicationService;
import com.scanales.eventflow.volunteers.VolunteerApplicationStatus;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UserSessionService;
import com.scanales.eventflow.util.AdminUtils;
import com.scanales.eventflow.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
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

    static native TemplateInstance volunteers(Event event, boolean volunteerSelected);

    static native TemplateInstance volunteersLounge(
        Event event, boolean loungeAccess, boolean eventAdmin, String loungeAccessReason);
  }

  @Inject EventService eventService;

  @Inject UsageMetricsService metrics;

  @Inject SecurityIdentity identity;

  @Inject UserSessionService sessionService;

  @Inject CfpFormOptionsService cfpFormOptionsService;

  @Inject CfpConfigService cfpConfigService;
  @Inject CfpEventConfigService cfpEventConfigService;
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
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/event/cfp", headers, context);
    currentUserId().ifPresent(userId -> gamificationService.award(userId, GamificationActivity.AGENDA_VIEW, id + ":cfp"));
    Event event = eventService.getEvent(id);
    CfpTimelineView cfpTimeline = null;
    if (event != null) {
      try {
        CfpEventConfigService.ResolvedEventConfig resolved = cfpEventConfigService.resolveForEvent(id);
        cfpTimeline =
            CfpTimelinePlanner.build(
                    event,
                    resolved.opensAt(),
                    resolved.closesAt(),
                    java.util.Locale.forLanguageTag(TemplateLocaleUtil.resolve(localeCookie, headers)))
                .orElse(null);
      } catch (Exception ignored) {
        cfpTimeline = null;
      }
    }
    return withLayoutData(
            Templates.cfp(
                event,
                cfpFormOptionsService.catalog(),
                cfpFormOptionsService.durationByFormat(),
                cfpTimeline),
            "eventos",
            localeCookie,
            headers)
        .data("cfpTestingModeEnabled", cfpConfigService != null && cfpConfigService.isTestingModeEnabled());
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
    currentUserId().ifPresent(userId -> gamificationService.award(userId, GamificationActivity.VOLUNTEER_VIEW, id));
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
        Templates.volunteers(event, volunteerSelected),
        "eventos",
        localeCookie,
        headers);
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
    currentUserId().ifPresent(userId -> gamificationService.award(userId, GamificationActivity.VOLUNTEER_VIEW, id));
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
