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
import com.scanales.eventflow.model.Event;
import jakarta.inject.Inject;

@Path("/event")
public class EventResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance detail(Event event);
    }

    @Inject
    EventService eventService;

    @Inject
    UsageMetricsService metrics;

    @GET
    @Path("{id}")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance event(@PathParam("id") String id,
            @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers) {
        String ua = headers.getHeaderString("User-Agent");
        metrics.recordPageView("/event", ua);
        metrics.recordEventView(id, ua);
        Event event = eventService.getEvent(id);
        if (event == null) {
            return Templates.detail(null);
        }
        return Templates.detail(event);
    }
}
