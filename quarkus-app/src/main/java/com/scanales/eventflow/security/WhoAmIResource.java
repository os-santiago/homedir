package com.scanales.eventflow.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

/** Temporary diagnostic endpoint to inspect the authenticated user. */
@Path("/whoami")
public class WhoAmIResource {

  @Inject SecurityIdentity identity;

  @GET
  @RolesAllowed("admin")
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, Object> whoami() {
    String sub = identity.getPrincipal().getName();
    String email = identity.getAttribute("email");
    return Map.of("sub", sub, "email", email);
  }
}
