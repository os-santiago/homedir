package com.scanales.eventflow.public_;

import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.util.AdminUtils;
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
public class EventScenarioResource {

  @Inject EventService eventService;
  @Inject UsageMetricsService metrics;
  @Inject SecurityIdentity identity;
  @Inject GamificationService gamificationService;

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
    currentUserId()
        .ifPresent(
            userId ->
                gamificationService.award(
                    userId, GamificationActivity.AGENDA_VIEW, eventId + ":" + id));
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
