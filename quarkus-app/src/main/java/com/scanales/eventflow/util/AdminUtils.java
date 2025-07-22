package com.scanales.eventflow.util;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;

/** Utility methods for admin checks. */
public final class AdminUtils {

    private AdminUtils() {
    }

    /**
     * Returns the list of admin emails configured in the {@code ADMIN_LIST}
     * configuration property. The value is expected to be a comma separated list
     * of email addresses.
     */
    public static List<String> getAdminList() {
        String raw = ConfigProvider.getConfig().getOptionalValue("ADMIN_LIST", String.class).orElse("");
        if (raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Returns {@code true} if the provided identity represents an authenticated
     * user whose email is present in the admin list.
     */
    public static boolean isAdmin(SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous()) {
            return false;
        }
        String email = getClaim(identity, "email");
        if (email == null) {
            email = identity.getPrincipal().getName();
        }
        return email != null && getAdminList().contains(email);
    }

    /** Obtains a claim or attribute from the identity. */
    public static String getClaim(SecurityIdentity identity, String claimName) {
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
