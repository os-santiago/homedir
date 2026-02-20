package com.scanales.eventflow.public_;

import com.scanales.eventflow.cfp.CfpFormCatalog;
import com.scanales.eventflow.cfp.CfpConfigService;
import com.scanales.eventflow.cfp.CfpFormOptionsService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UserSessionService;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/event")
public class EventResource {

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance detail(Event event);

    static native TemplateInstance cfp(Event event, CfpFormCatalog cfpCatalog, Map<String, Integer> cfpDurationByFormat);
  }

  @Inject EventService eventService;

  @Inject UsageMetricsService metrics;

  @Inject SecurityIdentity identity;

  @Inject UserSessionService sessionService;

  @Inject CfpFormOptionsService cfpFormOptionsService;

  @Inject CfpConfigService cfpConfigService;
  @Inject GamificationService gamificationService;

  @GET
  @Path("{id}")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance event(
      @PathParam("id") String id,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    String ua = headers.getHeaderString("User-Agent");
    String sessionId = context.session() != null ? context.session().id() : null;
    metrics.recordPageView("/event", sessionId, ua);
    metrics.recordEventView(id, sessionId, ua);
    currentUserId().ifPresent(userId -> gamificationService.award(userId, GamificationActivity.EVENT_VIEW, id));
    Event event = eventService.getEvent(id);
    return withLayoutData(Templates.detail(event), "eventos");
  }

  @GET
  @Path("{id}/cfp")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance cfp(
      @PathParam("id") String id,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/event/cfp", headers, context);
    currentUserId().ifPresent(userId -> gamificationService.award(userId, GamificationActivity.AGENDA_VIEW, id + ":cfp"));
    Event event = eventService.getEvent(id);
    return withLayoutData(Templates.cfp(event, cfpFormOptionsService.catalog(), cfpFormOptionsService.durationByFormat()), "eventos")
        .data("cfpTestingModeEnabled", cfpConfigService != null && cfpConfigService.isTestingModeEnabled());
  }

  private TemplateInstance withLayoutData(TemplateInstance templateInstance, String activePage) {
    boolean authenticated = identity != null && !identity.isAnonymous();
    String userName = authenticated ? identity.getPrincipal().getName() : null;
    return templateInstance
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
