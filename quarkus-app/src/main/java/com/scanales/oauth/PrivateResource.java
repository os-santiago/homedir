package com.scanales.oauth;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

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
        String sub = identity.getPrincipal().getName();
        String preferredUsername = identity.getAttribute("preferred_username");
        String name = identity.getAttribute("name");
        if (name == null) {
            name = sub;
        }
        String givenName = identity.getAttribute("given_name");
        String familyName = identity.getAttribute("family_name");
        String email = identity.getAttribute("email");
        String locale = identity.getAttribute("locale");
        String picture = identity.getAttribute("picture");

        return Templates.privatePage(sub, preferredUsername, name, givenName,
                familyName, email, locale, picture);
    }
}
