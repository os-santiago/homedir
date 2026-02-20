package com.scanales.eventflow.public_;

import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.util.AdminUtils;
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
import java.util.Locale;
import java.util.Optional;

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
  @Inject GamificationService gamificationService;
  @Inject SecurityIdentity identity;

  @GET
  @Path("/scenario/{id}")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance detail(
      @PathParam("id") String id,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/scenario", headers, context);
    currentUserId()
        .ifPresent(userId -> gamificationService.award(userId, GamificationActivity.AGENDA_VIEW, id));
    Scenario s = eventService.findScenario(id);
    if (s == null) {
      return Templates.detail(null, null, java.util.List.of());
    }
    var event = eventService.findEventByScenario(id);
    var talks = eventService.findTalksForScenario(id);
    metrics.recordStageVisit(id, event != null ? event.getTimezone() : null, headers, context);
    return Templates.detail(s, event, talks);
  }

  private Optional<String> currentUserId() {
    if (identity == null || identity.isAnonymous()) {
      return Optional.empty();
    }
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return Optional.of(email.toLowerCase(Locale.ROOT));
    }
    String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    if (principal == null || principal.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(principal.toLowerCase(Locale.ROOT));
  }
}
