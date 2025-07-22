package com.scanales.eventflow.private_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.jwt.JsonWebToken;
import jakarta.enterprise.inject.Instance;

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
    Instance<JsonWebToken> jwtInstance;


    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance profile() {
        identity.getAttributes().forEach((k, v) -> LOG.infov("{0} = {1}", k, v));

        JsonWebToken jwt = null;
        try {
            jwt = jwtInstance.get();
        } catch (Exception ex) {
            LOG.debugf("JWT unavailable: %s", ex.getMessage());
        }

        String name = null;
        String givenName = null;
        String familyName = null;
        String email = null;

        if (jwt != null) {
            name = jwt.getClaim("name");
            givenName = jwt.getClaim("given_name");
            familyName = jwt.getClaim("family_name");
            email = jwt.getClaim("email");
        }

        if (name == null) {
            name = identity.getAttribute("name");
        }
        if (name == null) {
            name = identity.getPrincipal().getName();
        }

        if (givenName == null) {
            givenName = identity.getAttribute("given_name");
        }
        if (familyName == null) {
            familyName = identity.getAttribute("family_name");
        }
        if (email == null) {
            email = identity.getAttribute("email");
        }

        String sub = identity.getPrincipal().getName();
        return Templates.profile(name, givenName, familyName, email, sub);
    }
}
