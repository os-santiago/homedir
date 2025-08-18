package com.scanales.eventflow.public_;

import com.scanales.eventflow.model.Scenario;
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

@Path("/scenario")
public class ScenarioResource {

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance detail(
        Scenario scenario,
        com.scanales.eventflow.model.Event event,
        java.util.List<com.scanales.eventflow.model.Talk> talks);
  }

  @Inject EventService eventService;

  @Inject UsageMetricsService metrics;

  @GET
  @Path("{id}")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance detail(
      @PathParam("id") String id,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/scenario", headers, context);
    Scenario s = eventService.findScenario(id);
    if (s == null) {
      return Templates.detail(null, null, java.util.List.of());
    }
    var event = eventService.findEventByScenario(id);
    var talks = eventService.findTalksForScenario(id);
    metrics.recordStageVisit(id, event != null ? event.getTimezone() : null, headers, context);
    return Templates.detail(s, event, talks);
  }
}
