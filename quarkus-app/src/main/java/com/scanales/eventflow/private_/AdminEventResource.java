package com.scanales.eventflow.private_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/private/admin/event")
public class AdminEventResource {

    private static final List<String> adminList = List.of("admin@example.com");

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance edit(String id);
    }

    @Inject
    SecurityIdentity identity;

    private boolean isAdmin() {
        OidcJwtCallerPrincipal principal = (OidcJwtCallerPrincipal) identity.getPrincipal();
        String email = getClaim(principal, "email");
        return email != null && adminList.contains(email);
    }

    private String getClaim(OidcJwtCallerPrincipal principal, String claimName) {
        Object value = principal.getClaim(claimName);
        return Optional.ofNullable(value).map(Object::toString).orElse(null);
    }

    @GET
    @Path("create")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response create() {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(Templates.edit(null)).build();
    }

    @GET
    @Path("{id}/edit")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response edit(@PathParam("id") String id) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(Templates.edit(id)).build();
    }
}
