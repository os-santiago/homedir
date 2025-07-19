package org.acme.oauth;

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
        public static native TemplateInstance privatePage(String name, String email, String picture);
    }

    @Inject
    SecurityIdentity identity;

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance privatePage() {
        String name = identity.getAttribute("name");
        if (name == null) {
            name = identity.getPrincipal().getName();
        }
        String email = identity.getAttribute("email");
        String picture = identity.getAttribute("picture");
        return Templates.privatePage(name, email, picture);
    }
}
