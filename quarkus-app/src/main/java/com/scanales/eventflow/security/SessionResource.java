package com.scanales.eventflow.security;

import io.quarkus.oidc.OidcSession;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** Simple endpoint to report session status. */
@Path("/auth/session")
public class SessionResource {

  @Inject SecurityIdentity identity;
  @Inject Instance<OidcSession> oidcSession;

  @GET
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, Object> session() {
    long exp = 0L;
    JsonWebToken jwt = null;
    if (identity.getPrincipal() instanceof JsonWebToken jw) {
      jwt = jw;
    } else if (oidcSession.isResolvable()) {
      jwt = oidcSession.get().getIdToken();
    }
    if (jwt != null) {
      Object claim = jwt.getClaim(Claims.exp.name());
      if (claim instanceof Long l) {
        exp = l;
      } else if (claim instanceof Integer i) {
        exp = i.longValue();
      }
    }
    return Map.of("active", true, "exp", exp);
  }

  @POST
  @Path("/refresh")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, Object> refresh() {
    // Simply accessing the ID token ensures the session remains active
    if (oidcSession.isResolvable()) {
      oidcSession.get().getIdToken();
    }
    return session();
  }
}
