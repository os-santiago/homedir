package org.acme.eventflow.util;

import java.time.LocalDate;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import io.quarkus.qute.TemplateExtension;
import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
@TemplateExtension(namespace = "app")
public class AppTemplateExtensions {

    @Inject
    SecurityIdentity identity;

    @Inject
    Config config;

    public int currentYear() {
        return LocalDate.now().getYear();
    }

    public String version() {
        return config.getOptionalValue("quarkus.application.version", String.class).orElse("dev");
    }

    public boolean isAuthenticated() {
        return identity != null && !identity.isAnonymous();
    }

    private static final List<String> adminList = List.of("sergio.canales.e@gmail.com");

    public boolean isAdmin() {
        if (identity == null || identity.isAnonymous()) {
            return false;
        }
        String email = identity.getAttribute("email");
        return email != null && adminList.contains(email);
    }
}
