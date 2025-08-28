package io.eventflow.notifications.rest;

import io.quarkus.security.identity.SecurityIdentity;

/** Helper to extract the authenticated user id. */
public final class SecurityIdentityUser {
  private SecurityIdentityUser() {}

  public static String id(SecurityIdentity identity) {
    if (identity == null || identity.isAnonymous()) {
      return null;
    }
    String email = identity.getAttribute("email");
    if (email == null && identity.getPrincipal() != null) {
      email = identity.getPrincipal().getName();
    }
    return email;
  }
}
