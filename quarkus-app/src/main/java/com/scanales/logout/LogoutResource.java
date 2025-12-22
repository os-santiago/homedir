package com.scanales.logout;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.Logger;

import jakarta.ws.rs.core.NewCookie;

/**
 * Simple endpoint to clear the q_session cookie and redirect the user to the
 * home page.
 */
@Path("/logout")
public class LogoutResource {

  private static final Logger LOG = Logger.getLogger(LogoutResource.class);

  @GET
  public Response logout(
      @Context UriInfo uriInfo, @Context jakarta.ws.rs.core.HttpHeaders headers) {
    LOG.info("Processing logout request");

    // Determine scheme and security
    String forwardedProto = headers.getHeaderString("X-Forwarded-Proto");
    String scheme = forwardedProto != null
        ? forwardedProto
        : (uriInfo != null ? uriInfo.getRequestUri().getScheme() : "http");
    boolean secure = "https".equalsIgnoreCase(scheme);

    String sameSite = secure ? "None" : "Lax";

    // Use NewCookie for reliable clearing
    NewCookie clearSession = new NewCookie.Builder("q_session")
        .path("/")
        .maxAge(0)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax".equalsIgnoreCase(sameSite) ? NewCookie.SameSite.LAX : NewCookie.SameSite.NONE)
        .build();

    // Fallback for q_session (Lax)
    NewCookie clearSessionFallback = new NewCookie.Builder("q_session")
        .path("/")
        .maxAge(0)
        .httpOnly(true)
        .secure(false) // Try specifically non-secure
        .sameSite(NewCookie.SameSite.LAX)
        .build();

    // Clear quarkus-credential (standard Form Auth - Path /)
    NewCookie clearCredential = new NewCookie.Builder("quarkus-credential")
        .path("/")
        .maxAge(0)
        .httpOnly(true)
        .secure(false)
        .sameSite(NewCookie.SameSite.LAX)
        .build();

    return Response.status(Response.Status.SEE_OTHER)
        .header(HttpHeaders.LOCATION, "/")
        .cookie(clearSession, clearSessionFallback, clearCredential)
        .build();
  }
}
