package com.scanales.eventflow.util;

import java.time.LocalDate;
import java.util.List;

import io.quarkus.arc.Arc;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import io.quarkus.qute.TemplateExtension;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;

import java.util.Optional;

@TemplateExtension(namespace = "app")
public class AppTemplateExtensions {

    public static int currentYear() {
        return LocalDate.now().getYear();
    }

    public static String version() {
        try {
            Config config = ConfigProvider.getConfig();
            return config.getOptionalValue("quarkus.application.version", String.class).orElse("dev");
        } catch (Exception e) {
            return "dev";
        }
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
        String email = getClaim(identity, "email");
        return email != null && adminList.contains(email);
    }

    private static String getClaim(SecurityIdentity identity, String claimName) {
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
