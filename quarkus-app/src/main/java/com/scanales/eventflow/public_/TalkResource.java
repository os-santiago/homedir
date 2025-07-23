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
import com.scanales.eventflow.model.Talk;
import jakarta.inject.Inject;

@Path("/talk")
public class TalkResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance detail(Talk talk,
                                             com.scanales.eventflow.model.Event event,
                                             java.util.List<Talk> occurrences);
    }

    @Inject
    EventService eventService;

    @GET
    @Path("{id}")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance detail(@PathParam("id") String id) {
        Talk talk = eventService.findTalk(id);
        if (talk == null) {
            return Templates.detail(null, null, java.util.List.of());
        }
        var event = eventService.findEventByTalk(id);
        var occurrences = eventService.findTalkOccurrences(id);
        return Templates.detail(talk, event, occurrences);
    }
}
