package com.scanales.eventflow.public_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.model.Scenario;
import jakarta.inject.Inject;

@Path("/scenario")
public class ScenarioResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance detail(Scenario scenario,
                                             com.scanales.eventflow.model.Event event,
                                             java.util.List<com.scanales.eventflow.model.Talk> talks);
    }

    @Inject
    EventService eventService;

    @Inject
    UsageMetricsService metrics;

    @GET
    @Path("{id}")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance detail(@PathParam("id") String id,
            @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
            @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
        String ua = headers.getHeaderString("User-Agent");
        String sessionId = context.session() != null ? context.session().id() : null;
        metrics.recordPageView("/scenario", sessionId, ua);
        Scenario s = eventService.findScenario(id);
        if (s == null) {
            return Templates.detail(null, null, java.util.List.of());
        }
        var event = eventService.findEventByScenario(id);
        var talks = eventService.findTalksForScenario(id);
        metrics.recordStageVisit(id, event != null ? event.getTimezone() : null, sessionId, ua);
        return Templates.detail(s, event, talks);
    }
}
