package com.scanales.eventflow.public_;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UsageMetricsService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
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
  }

  @Inject EventService eventService;

  @Inject UsageMetricsService metrics;

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
    if (event == null) {
      return Templates.detail(null);
    }
    return Templates.detail(event);
  }
}
