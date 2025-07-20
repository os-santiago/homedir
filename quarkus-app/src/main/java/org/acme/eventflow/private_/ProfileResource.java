package org.acme.eventflow.private_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/private/profile")
public class ProfileResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance profile(String name, String email, String sub);
    }

    @Inject
    SecurityIdentity identity;

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance profile() {
        String name = identity.getAttribute("name");
        String email = identity.getAttribute("email");
        String sub = identity.getPrincipal().getName();
        return Templates.profile(name, email, sub);
    }
}
