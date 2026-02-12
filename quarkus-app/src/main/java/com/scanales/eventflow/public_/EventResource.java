package com.scanales.eventflow.public_;

import com.scanales.eventflow.cfp.CfpFormCatalog;
import com.scanales.eventflow.cfp.CfpFormOptionsService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UserSessionService;
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

@Path("/event")
public class EventResource {

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance detail(Event event);

    static native TemplateInstance cfp(Event event, CfpFormCatalog cfpCatalog);
  }

  @Inject EventService eventService;

  @Inject UsageMetricsService metrics;

  @Inject SecurityIdentity identity;

  @Inject UserSessionService sessionService;

  @Inject CfpFormOptionsService cfpFormOptionsService;

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
    Event event = eventService.getEvent(id);
    return withLayoutData(Templates.cfp(event, cfpFormOptionsService.catalog()), "eventos");
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
}