package com.scanales.eventflow.private_;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.net.URI;

/** Redirects authenticated users back to a requested page after login. */
@Path("/private/login-callback")
public class LoginCallbackResource {

  @GET
  @Authenticated
  public Response callback(@QueryParam("redirect") String redirect) {
    if (redirect == null || redirect.isBlank()) {
      redirect = "/";
    }
    return Response.seeOther(URI.create(redirect)).build();
  }
}
