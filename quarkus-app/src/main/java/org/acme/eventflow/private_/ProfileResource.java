package org.acme.eventflow.private_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
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

    @Inject
    JsonWebToken jwt;

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance profile() {
        identity.getAttributes().forEach((k, v) -> LOG.infov("{0} = {1}", k, v));

        String name = jwt.getClaim("name");
        if (name == null) {
            name = identity.getAttribute("name");
        }

        String givenName = jwt.getClaim("given_name");
        String familyName = jwt.getClaim("family_name");

        String email = jwt.getClaim("email");
        if (email == null) {
            email = identity.getAttribute("email");
        }

        String sub = identity.getPrincipal().getName();
        return Templates.profile(name, givenName, familyName, email, sub);
    }
}
