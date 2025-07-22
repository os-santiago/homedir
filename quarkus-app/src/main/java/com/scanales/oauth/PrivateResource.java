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

/**
 * OAuth-protected resource that shows the authenticated user's information.
 * It now lives under "/private" so it does not clash with other endpoints.
 */
@Path("/private")
public class PrivateResource {

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
        OidcJwtCallerPrincipal principal = (OidcJwtCallerPrincipal) identity.getPrincipal();

        String sub = getClaim(principal, "sub");
        String preferredUsername = getClaim(principal, "preferred_username");
        String name = getClaim(principal, "name");
        if (name == null) {
            name = sub;
        }
        String givenName = getClaim(principal, "given_name");
        String familyName = getClaim(principal, "family_name");
        String email = getClaim(principal, "email");
        String locale = getClaim(principal, "locale");
        String picture = getClaim(principal, "picture");

        return Templates.privatePage(sub, preferredUsername, name, givenName,
                familyName, email, locale, picture);
    }

    private String getClaim(OidcJwtCallerPrincipal principal, String claimName) {
        Object value = principal.getClaim(claimName);
        return Optional.ofNullable(value).map(Object::toString).orElse(null);
    }
}
