package com.scanales.eventflow.public_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.SpeakerService;

@Path("/speaker")
public class SpeakerResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance detail(Speaker speaker,
                Map<String, List<Event>> talkEvents);
    }

    @Inject
    SpeakerService speakerService;

    @Inject
    EventService eventService;

    @GET
    @Path("{id}")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance detail(@PathParam("id") String id) {
        Speaker sp = speakerService.getSpeaker(id);
        Map<String, List<Event>> talkEvents = new HashMap<>();
        if (sp != null && sp.getTalks() != null) {
            for (Talk t : sp.getTalks()) {
                List<Event> events = eventService.findEventsByTalk(t.getId());
                if (!events.isEmpty()) {
                    talkEvents.put(t.getId(), events);
                }
            }
        }
        return Templates.detail(sp, talkEvents);
    }
}
