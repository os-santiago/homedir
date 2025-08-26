package com.scanales.eventflow.public_;

import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UsageMetricsService;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/event")
public class EventScenarioResource {

  @Inject EventService eventService;
  @Inject UsageMetricsService metrics;

  @GET
  @Path("{eventId}/scenario/{id}")
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
      return ScenarioResource.Templates.detail(null, null, java.util.List.of());
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
    return ScenarioResource.Templates.detail(scenario, event, talks);
  }
}
