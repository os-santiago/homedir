package com.scanales.eventflow.private_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;

import java.util.Optional;
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
        OidcJwtCallerPrincipal principal = (OidcJwtCallerPrincipal) identity.getPrincipal();

        String name = getClaim(principal, "name");
        String givenName = getClaim(principal, "given_name");
        String familyName = getClaim(principal, "family_name");
        String email = getClaim(principal, "email");

        if (name == null) {
            name = principal.getName();
        }

        String sub = principal.getName();
        return Templates.profile(name, givenName, familyName, email, sub);
    }

    private String getClaim(OidcJwtCallerPrincipal principal, String claimName) {
        Object value = principal.getClaim(claimName);
        return Optional.ofNullable(value).map(Object::toString).orElse(null);
    }
}
