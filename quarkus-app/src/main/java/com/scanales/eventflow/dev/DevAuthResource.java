package com.scanales.eventflow.dev;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.qute.Location;

@Path("/login.html")
@IfBuildProfile("dev")
public class DevAuthResource {

    @Inject
    @Location("dev-login.html")
    Template devLogin;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance login() {
        return devLogin.instance();
    }
}
