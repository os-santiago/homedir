package com.scanales.oauth;

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
import org.jboss.logging.Logger;

/**
 * OAuth-protected resource that shows the authenticated user's information.
 * It now lives under "/private" so it does not clash with other endpoints.
 */
@Path("/private")
public class PrivateResource {

    private static final Logger LOG = Logger.getLogger(PrivateResource.class);
    private static final String PREFIX = "[LOGIN] ";

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance privatePage(String sub,
                String preferredUsername,
                String name,
                String givenName,
                String familyName,
                String email,
                String locale,
                String picture);
    }

    @Inject
    SecurityIdentity identity;

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance privatePage() {
        LOG.info(PREFIX + "Serving private page");
        String sub = getClaim("sub");
        if (sub == null) {
            sub = identity.getPrincipal().getName();
        }
        String preferredUsername = getClaim("preferred_username");
        String name = getClaim("name");
        if (name == null) {
            name = sub;
        }
        String givenName = getClaim("given_name");
        String familyName = getClaim("family_name");
        String email = getClaim("email");
        String locale = getClaim("locale");
        String picture = getClaim("picture");

        return Templates.privatePage(sub, preferredUsername, name, givenName,
                familyName, email, locale, picture);
    }

    private String getClaim(String claimName) {
        Object value = null;
        if (identity.getPrincipal() instanceof OidcJwtCallerPrincipal oidc) {
            value = oidc.getClaim(claimName);
        }
        if (value == null) {
            value = identity.getAttribute(claimName);
        }
        return Optional.ofNullable(value).map(Object::toString).orElse(null);
    }
}
