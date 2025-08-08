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

import com.scanales.eventflow.service.SpeakerService;
import com.scanales.eventflow.model.Speaker;

@Path("/speaker")
public class SpeakerResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance detail(Speaker speaker);
    }

    @Inject
    SpeakerService speakerService;

    @GET
    @Path("{id}")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance detail(@PathParam("id") String id) {
        Speaker sp = speakerService.getSpeaker(id);
        return Templates.detail(sp);
    }
}
