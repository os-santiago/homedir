package com.scanales.eventflow.private_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/private/profile")
public class ProfileResource {

    private static final Logger LOG = Logger.getLogger(ProfileResource.class);

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance profile(String name,
                String givenName,
                String familyName,
                String email,
                String sub);
    }

    @Inject
    SecurityIdentity identity;

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance profile() {
        identity.getAttributes().forEach((k, v) -> LOG.infov("{0} = {1}", k, v));
        String name = identity.getAttribute("name");
        String givenName = identity.getAttribute("given_name");
        String familyName = identity.getAttribute("family_name");
        String email = identity.getAttribute("email");

        if (name == null) {
            name = identity.getPrincipal().getName();
        }

        String sub = identity.getPrincipal().getName();
        return Templates.profile(name, givenName, familyName, email, sub);
    }
}
