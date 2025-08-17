package com.scanales.eventflow.security;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** Simple endpoint to report session status. */
@Path("/auth/session")
public class SessionResource {

  @Inject SecurityIdentity identity;

  @GET
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, Object> session() {
    long exp = 0L;
    JsonWebToken jwt = null;
    if (identity.getPrincipal() instanceof JsonWebToken jw) {
      jwt = jw;
    }
    if (jwt != null) {
      exp = jwt.getExpirationTime();
    }
    return Map.of("active", true, "exp", exp);
  }
}
