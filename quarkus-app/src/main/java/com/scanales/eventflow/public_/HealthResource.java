package com.scanales.eventflow.public_;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/health")
public class HealthResource {

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_PLAIN)
  public Response health() {
    return Response.ok("ok").build();
  }

  @GET
  @Path("/ready")
  @PermitAll
  @Produces(MediaType.TEXT_PLAIN)
  public Response ready() {
    return Response.ok("ready").build();
  }
}
