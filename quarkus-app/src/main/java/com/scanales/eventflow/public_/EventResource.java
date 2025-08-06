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
import com.scanales.eventflow.model.Event;
import jakarta.inject.Inject;
import io.quarkus.security.identity.SecurityIdentity;
import com.scanales.eventflow.service.UserScheduleService;

@Path("/event")
public class EventResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance detail(Event event);
        static native TemplateInstance agenda(Event event, java.util.Set<String> userTalks);
    }

    @Inject
    EventService eventService;

    @Inject
    SecurityIdentity identity;

    @Inject
    UserScheduleService userSchedule;

    @GET
    @Path("{id}")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance event(@PathParam("id") String id) {
        Event event = eventService.getEvent(id);
        if (event == null) {
            return Templates.detail(null);
        }
        return Templates.detail(event);
    }

    @GET
    @Path("{id}/agenda")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance agenda(@PathParam("id") String id) {
        Event event = eventService.getEvent(id);
        java.util.Set<String> talks = java.util.Set.of();
        if (identity != null && !identity.isAnonymous()) {
            String email = identity.getAttribute("email");
            if (email == null) {
                email = identity.getPrincipal().getName();
            }
            talks = userSchedule.getTalksForUser(email);
        }
        return Templates.agenda(event, talks);
    }
}
