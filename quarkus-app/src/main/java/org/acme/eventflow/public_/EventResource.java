package org.acme.eventflow.public_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/event")
public class EventResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance detail(String id);
    }

    @GET
    @Path("{id}")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance event(@PathParam("id") String id) {
        return Templates.detail(id);
    }
}
