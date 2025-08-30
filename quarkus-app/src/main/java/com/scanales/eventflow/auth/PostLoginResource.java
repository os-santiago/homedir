package com.scanales.eventflow.auth;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.net.URI;

/** Handles the post-login redirect from OIDC. */
@Path("/auth/post-login")
public class PostLoginResource {

  @Inject SecurityIdentity identity;

  @GET
  public Response afterLogin() {
    if (identity != null && !identity.isAnonymous()) {
      return Response.seeOther(URI.create("/profile")).build();
    }
    return Response.seeOther(URI.create("/ingresar?retry=1")).build();
  }
}
