package com.scanales.homedir.public_;

import com.scanales.homedir.service.EventService;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.service.GamificationService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.util.AdminUtils;
import com.scanales.homedir.util.TemplateLocaleUtil;
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
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
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
                java.util.Comparator.comparingInt(com.scanales.homedir.model.Talk::getDay)
                    .thenComparing(com.scanales.homedir.model.Talk::getStartTime))
            .toList();
    metrics.recordStageVisit(id, event.getTimezone(), headers, context);
    return TemplateLocaleUtil.apply(ScenarioResource.Templates.detail(scenario, event, talks), localeCookie);
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
