package com.scanales.eventflow.private_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import com.scanales.eventflow.util.AdminUtils;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/private/admin")
public class AdminResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance admin(String name);
        static native TemplateInstance guide();
    }

    @Inject
    SecurityIdentity identity;

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response admin() {
        String email = AdminUtils.getClaim(identity, "email");
        if (email == null || !AdminUtils.getAdminList().contains(email)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        String name = AdminUtils.getClaim(identity, "name");
        if (name == null) {
            name = email;
        }
        return Response.ok(Templates.admin(name)).build();
    }

    @GET
    @Path("guide")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response guide() {
        if (!AdminUtils.isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(Templates.guide()).build();
    }
}
