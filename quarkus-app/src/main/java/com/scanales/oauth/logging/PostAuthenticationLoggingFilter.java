package com.scanales.oauth.logging;

import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Optional;

/** Logs token information and user details after a successful authentication. */
@Provider
@Priority(Priorities.AUTHENTICATION + 1)
public class PostAuthenticationLoggingFilter extends AbstractLoggingFilter {

  @Inject SecurityIdentity identity;

  @Override
  protected void handle(ContainerRequestContext requestContext) throws IOException {
    if (identity == null || identity.isAnonymous()) {
      return;
    }

    String sub = getClaim("sub");
    if (sub == null) {
      sub = identity.getPrincipal().getName();
    }
    String preferredUsername = getClaim("preferred_username");
    String name = getClaim("name");
    String givenName = getClaim("given_name");
    String familyName = getClaim("family_name");
    String email = getClaim("email");
    String locale = getClaim("locale");
    String picture = getClaim("picture");

    checkAttribute("sub", sub);
    checkAttribute("preferred_username", preferredUsername);
    checkAttribute("name", name);
    checkAttribute("given_name", givenName);
    checkAttribute("family_name", familyName);
    checkAttribute("email", email);
    checkAttribute("locale", locale);
    checkAttribute("picture", picture);

    log.infof(
        "User authenticated: sub=%s preferred_username=%s email=%s", sub, preferredUsername, email);
  }

  private String getClaim(String claimName) {
    Object value = null;
    if (identity.getPrincipal() instanceof OidcJwtCallerPrincipal oidc) {
      value = oidc.getClaim(claimName);
    }
    if (value == null) {
      value = identity.getAttribute(claimName);
    }
    return Optional.ofNullable(value).map(Object::toString).orElse(null);
  }

  private void checkAttribute(String attrName, String value) {
    if (value == null || value.isBlank()) {
      log.warnf("Missing OIDC claim: %s", attrName);
    }
  }
}
