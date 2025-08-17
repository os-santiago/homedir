package com.scanales.eventflow.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** Redirects unauthenticated or expired HTML requests to the home page. */
@Provider
@PreMatching
@Priority(Priorities.AUTHORIZATION)
public class HtmlSessionExpiryFilter implements ContainerRequestFilter {

  private static final Set<String> PUBLIC_PATHS = Set.of("/", "/health", "/metrics");
  private static final Pattern STATIC_PATTERN = Pattern.compile("^/(css|js|images|static|img)/.*");

  @Inject SecurityIdentity identity;

  @ConfigProperty(name = "app.auth.redirect-on-expire", defaultValue = "true")
  boolean redirectOnExpire;

  @Override
  public void filter(ContainerRequestContext ctx) {
    if (!redirectOnExpire) {
      return;
    }
    String path = "/" + Optional.ofNullable(ctx.getUriInfo().getPath()).orElse("");
    String accept = Optional.ofNullable(ctx.getHeaderString(HttpHeaders.ACCEPT)).orElse("");

    boolean isHtml = accept.contains(MediaType.TEXT_HTML);
    boolean isPublic = PUBLIC_PATHS.contains(path) || STATIC_PATTERN.matcher(path).matches();

    if (!isHtml || isPublic) {
      return;
    }

    boolean anonymous = identity == null || identity.isAnonymous();
    boolean expired = isExpired(identity);

    if (anonymous || expired) {
      ctx.abortWith(
          Response.status(Response.Status.FOUND)
              .header(HttpHeaders.LOCATION, "/")
              .header("X-Redirected-By", "session-expired")
              .header(HttpHeaders.SET_COOKIE, "q_session=; Path=/; Max-Age=0; HttpOnly; Secure")
              .build());
    }
  }

  private boolean isExpired(SecurityIdentity id) {
    if (id == null) {
      return true;
    }
    JsonWebToken jwt = null;
    if (id.getPrincipal() instanceof JsonWebToken jw) {
      jwt = jw;
    }
    if (jwt == null) {
      return false;
    }
    Long exp = jwt.getExpirationTime();
    return exp != null && exp <= (System.currentTimeMillis() / 1000L);
  }
}

