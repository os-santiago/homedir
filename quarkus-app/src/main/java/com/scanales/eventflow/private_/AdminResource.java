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
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/private/admin")
public class AdminResource {

    private static final List<String> adminList = List.of("sergio.canales.e@gmail.com");

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance admin(String name);
    }

    @Inject
    SecurityIdentity identity;

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response admin() {
        OidcJwtCallerPrincipal principal = (OidcJwtCallerPrincipal) identity.getPrincipal();
        String email = getClaim(principal, "email");
        if (email == null || !adminList.contains(email)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        String name = getClaim(principal, "name");
        if (name == null) {
            name = email;
        }
        return Response.ok(Templates.admin(name)).build();
    }

    private String getClaim(OidcJwtCallerPrincipal principal, String claimName) {
        Object value = principal.getClaim(claimName);
        return Optional.ofNullable(value).map(Object::toString).orElse(null);
    }
}
