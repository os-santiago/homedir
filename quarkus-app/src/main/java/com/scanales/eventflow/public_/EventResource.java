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
import org.jboss.logging.Logger;

@Path("/event")
public class EventResource {

    private static final Logger LOG = Logger.getLogger(EventResource.class);
    private static final String PREFIX = "[WEB] ";

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance detail(Event event);
        static native TemplateInstance agenda(Event event);
    }

    @Inject
    EventService eventService;

    @GET
    @Path("{id}")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance event(@PathParam("id") String id) {
        LOG.infof(PREFIX + "Loading event %s", id);
        Event event = eventService.getEvent(id);
        if (event == null) {
            LOG.warnf(PREFIX + "Event %s not found", id);
            return Templates.detail(null);
        }
        return Templates.detail(event);
    }

    @GET
    @Path("{id}/agenda")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance agenda(@PathParam("id") String id) {
        LOG.infof(PREFIX + "Loading agenda for event %s", id);
        Event event = eventService.getEvent(id);
        return Templates.agenda(event);
    }
}
