package org.acme.firebase;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/")
public class GreetingResource {

    @Inject
    Template login;

    @Inject
    @io.quarkus.qute.Location("protected")
    Template protectedPage;

    @Inject
    SecurityIdentity identity;

    @ConfigProperty(name = "firebase.api-key")
    String apiKey;

    @ConfigProperty(name = "firebase.auth-domain")
    String authDomain;

    @ConfigProperty(name = "firebase.project-id")
    String projectId;

    @GET
    @Path("login")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance login() {
        return login.data("apiKey", apiKey)
                .data("authDomain", authDomain)
                .data("projectId", projectId);
    }

    @GET
    @Path("protected")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance protectedPage() {
        return protectedPage.data("name", identity.getPrincipal().getName());
    }
}
