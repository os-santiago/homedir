package com.scanales.eventflow.public_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import com.scanales.eventflow.service.EventService;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@Path("/")
public class HomeResource {

    private static final Logger LOG = Logger.getLogger(HomeResource.class);
    private static final String PREFIX = "[WEB] ";

    @Inject
    EventService eventService;

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance home(java.util.List<com.scanales.eventflow.model.Event> events);
    }

    @GET
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance home() {
        LOG.info(PREFIX + "Loading home page");
        var events = eventService.listEvents();
        return Templates.home(events);
    }
}
