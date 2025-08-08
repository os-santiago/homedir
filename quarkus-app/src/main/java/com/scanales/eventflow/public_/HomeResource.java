package com.scanales.eventflow.public_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.LocalDate;
import java.util.Comparator;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import jakarta.inject.Inject;

@Path("/")
public class HomeResource {

    @Inject
    EventService eventService;

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance home(java.util.List<com.scanales.eventflow.model.Event> events,
                LocalDate today);
    }

    @GET
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance home() {
        var events = eventService.listEvents().stream()
                .sorted(Comparator.comparing(Event::getDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return Templates.home(events, LocalDate.now());
    }

    @GET
    @Path("/events")
    @PermitAll
    public Response legacyEvents() {
        return Response.status(Response.Status.MOVED_PERMANENTLY)
                .location(URI.create("/"))
                .build();
    }
}
