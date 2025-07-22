package com.scanales.eventflow.public_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/resources")
public class ResourcesPage {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance index();
    }

    @GET
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        return Templates.index();
    }
}
