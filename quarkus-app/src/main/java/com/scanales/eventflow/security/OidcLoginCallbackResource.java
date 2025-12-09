package com.scanales.eventflow.security;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.net.URI;

/**
 * OIDC callback endpoint that Quarkus hits after the IdP redirects back.
 *
 * <p>This must exist because the configured redirect path is /login/callback. Once the
 * authentication succeeds, we bounce the user to a safe in-app location.
 */
@Path("/login/callback")
public class OidcLoginCallbackResource {

  private static final String DEFAULT_REDIRECT = "/private/profile";

  @GET
  @Authenticated
  public Response handle(@QueryParam("redirect") String redirect) {
    String target = (redirect == null || redirect.isBlank()) ? DEFAULT_REDIRECT : redirect;
    return Response.seeOther(URI.create(target)).build();
  }
}
