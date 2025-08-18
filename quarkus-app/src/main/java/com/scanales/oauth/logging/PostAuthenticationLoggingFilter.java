package com.scanales.oauth.logging;

import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.oidc.IdTokenCredential;
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

    IdTokenCredential idToken = identity.getCredential(IdTokenCredential.class);
    if (idToken != null) {
      log.infov("ID Token: {0}", idToken.getToken());
      String token = idToken.getToken();
      String[] parts = token.split("\\.");
      if (parts.length >= 2) {
        String claimsJson =
            new String(
                java.util.Base64.getUrlDecoder().decode(parts[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        log.infov("ID Token claims: {0}", claimsJson);
      }
    }

    AccessTokenCredential accessToken = identity.getCredential(AccessTokenCredential.class);
    if (accessToken != null) {
      log.infov("Access Token: {0}", accessToken.getToken());
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
        "User Authenticated:%n"
            + "sub: %s%n"
            + "preferred_username: %s%n"
            + "name: %s%n"
            + "given_name: %s%n"
            + "family_name: %s%n"
            + "email: %s%n"
            + "locale: %s%n"
            + "picture: %s",
        sub, preferredUsername, name, givenName, familyName, email, locale, picture);
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
