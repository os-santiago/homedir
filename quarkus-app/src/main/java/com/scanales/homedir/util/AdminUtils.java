package com.scanales.homedir.util;

import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.security.identity.SecurityIdentity;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.ConfigProvider;

/** Utility methods for admin checks. */
public final class AdminUtils {

  public static final String ADMIN_ROLE = "admin";
  public static final String ADMIN_VIEW_ROLE = "admin-view";

  private AdminUtils() {}

  /**
   * Returns the list of admin emails configured in the {@code ADMIN_LIST} configuration property.
   * The value is expected to be a comma separated list of email addresses.
   */
  public static List<String> getAdminList() {
    String raw = ConfigProvider.getConfig().getOptionalValue("ADMIN_LIST", String.class).orElse("");
    if (raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  /**
   * Returns the list of viewer emails configured in the {@code ADMIN_VIEW_LIST} configuration
   * property. These identities may access admin backoffice read surfaces, but they cannot modify
   * state.
   */
  public static List<String> getAdminViewList() {
    String raw =
        ConfigProvider.getConfig().getOptionalValue("ADMIN_VIEW_LIST", String.class).orElse("");
    if (raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  /**
   * Returns {@code true} if the provided identity represents an authenticated user whose email is
   * present in the admin list.
   */
  public static boolean isAdmin(SecurityIdentity identity) {
    if (!isAuthenticated(identity)) {
      return false;
    }
    if (identity.getRoles().contains(ADMIN_ROLE)) {
      return true;
    }
    String email = resolveEmail(identity);
    return email != null && getAdminList().contains(email);
  }

  /**
   * Returns {@code true} if the provided identity can access admin backoffice read surfaces. Full
   * admins are implicitly viewers.
   */
  public static boolean canViewAdminBackoffice(SecurityIdentity identity) {
    if (!isAuthenticated(identity)) {
      return false;
    }
    if (identity.getRoles().contains(ADMIN_ROLE) || identity.getRoles().contains(ADMIN_VIEW_ROLE)) {
      return true;
    }
    String email = resolveEmail(identity);
    return email != null && (getAdminList().contains(email) || getAdminViewList().contains(email));
  }

  /** Returns {@code true} if the provided identity can change admin backoffice state. */
  public static boolean canManageAdminBackoffice(SecurityIdentity identity) {
    return isAdmin(identity);
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

  private static boolean isAuthenticated(SecurityIdentity identity) {
    return identity != null && !identity.isAnonymous();
  }

  private static String resolveEmail(SecurityIdentity identity) {
    String email = getClaim(identity, "email");
    if (email == null) {
      email = identity.getPrincipal().getName();
    }
    return email;
  }
}
