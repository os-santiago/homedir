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

@Path("")
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
  @Path("/scenario/{id}")
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

  /**
   * Nueva ruta: /event/{eventId}/scenario/{id}
   * Permite mostrar el escenario en el contexto del evento de origen.
   */
  @GET
  @Path("/event/{eventId}/scenario/{id}")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance detailWithEvent(
      @PathParam("eventId") String eventId,
      @PathParam("id") String id,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/event/" + eventId + "/scenario", headers, context);
    var event = eventService.getEvent(eventId);
    if (event == null) {
      return Templates.detail(null, null, java.util.List.of());
    }
    var scenario =
        event.getScenarios().stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
    var talks =
        event.getAgenda().stream()
            .filter(t -> id.equals(t.getLocation()))
            .sorted(
                java.util.Comparator.comparingInt(com.scanales.eventflow.model.Talk::getDay)
                    .thenComparing(com.scanales.eventflow.model.Talk::getStartTime))
            .toList();
    metrics.recordStageVisit(id, event.getTimezone(), headers, context);
    return Templates.detail(scenario, event, talks);
  }
}
