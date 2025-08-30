package com.scanales.eventflow.auth;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.net.URI;

/** Public entry point that redirects to the protected profile page. */
@Path("/profile")
@PermitAll
public class ProfileAliasResource {

  @GET
  public Response redirect() {
    return Response.seeOther(URI.create("/private/profile")).build();
  }
}
