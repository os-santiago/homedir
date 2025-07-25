package com.scanales.eventflow.public_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@Path("/login")
public class LoginPage {

    private static final Logger LOG = Logger.getLogger(LoginPage.class);
    private static final String PREFIX = "[LOGIN] ";

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance login();
    }

    @GET
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance login() {
        LOG.info(PREFIX + "Serving login page");
        return Templates.login();
    }
}
