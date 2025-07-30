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
import com.scanales.eventflow.model.Scenario;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@Path("/scenario")
public class ScenarioResource {

    private static final Logger LOG = Logger.getLogger(ScenarioResource.class);
    private static final String PREFIX = "[WEB] ";

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance detail(Scenario scenario,
                                             com.scanales.eventflow.model.Event event,
                                             java.util.List<com.scanales.eventflow.model.Talk> talks);
    }

    @Inject
    EventService eventService;

    @GET
    @Path("{id}")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance detail(@PathParam("id") String id) {
        LOG.infof(PREFIX + "Loading scenario %s", id);
        Scenario s = eventService.findScenario(id);
        if (s == null) {
            LOG.warnf(PREFIX + "Scenario %s not found", id);
            return Templates.detail(null, null, java.util.List.of());
        }
        var event = eventService.findEventByScenario(id);
        var talks = eventService.findTalksForScenario(id);
        return Templates.detail(s, event, talks);
    }
}
