package org.acme.eventflow.util;

import java.time.LocalDate;
import java.util.List;

import io.quarkus.arc.Arc;
import org.eclipse.microprofile.config.Config;
import io.quarkus.qute.TemplateExtension;
import io.quarkus.security.identity.SecurityIdentity;

@TemplateExtension(namespace = "app")
public class AppTemplateExtensions {

    public static int currentYear() {
        return LocalDate.now().getYear();
    }

    public static String version() {
        Config config = Arc.container().instance(Config.class).get();
        return config.getOptionalValue("quarkus.application.version", String.class).orElse("dev");
    }

    public static boolean isAuthenticated() {
        SecurityIdentity identity = Arc.container().instance(SecurityIdentity.class).get();
        return identity != null && !identity.isAnonymous();
    }

    private static final List<String> adminList = List.of("sergio.canales.e@gmail.com");

    public static boolean isAdmin() {
        SecurityIdentity identity = Arc.container().instance(SecurityIdentity.class).get();
        if (identity == null || identity.isAnonymous()) {
            return false;
        }
        String email = identity.getAttribute("email");
        return email != null && adminList.contains(email);
    }
}
