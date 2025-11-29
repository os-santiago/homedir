package com.scanales.logout;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.Logger;

/** Simple endpoint to clear the q_session cookie and redirect the user to the home page. */
@Path("/logout")
public class LogoutResource {

  private static final Logger LOG = Logger.getLogger(LogoutResource.class);

  @GET
  public Response logout(@Context UriInfo uriInfo, @Context jakarta.ws.rs.core.HttpHeaders headers) {
    LOG.info("Processing logout request");

    String forwardedProto = headers.getHeaderString("X-Forwarded-Proto");
    String scheme =
        forwardedProto != null
            ? forwardedProto
            : (uriInfo != null ? uriInfo.getRequestUri().getScheme() : "http");
    boolean secure = "https".equalsIgnoreCase(scheme);
    String sameSite = secure ? "None" : "Lax";

    StringBuilder setCookie =
        new StringBuilder("q_session=; Path=/; Max-Age=0; HttpOnly; SameSite=").append(sameSite);
    if (secure) {
      setCookie.append("; Secure");
    }

    return Response.status(Response.Status.SEE_OTHER)
        .header(HttpHeaders.LOCATION, "/")
        .header(HttpHeaders.SET_COOKIE, setCookie.toString())
        .build();
  }
}
