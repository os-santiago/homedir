package com.scanales.eventflow.private_;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/private/discord")
@Authenticated
public class DiscordLinkResource {

  @Inject SecurityIdentity identity;

  @Inject DiscordLinkService service;

  @GET
  @Path("start")
  @Produces(MediaType.TEXT_HTML)
  public Response start(@QueryParam("redirect") String redirect) {
    return service.start(identity, redirect);
  }
}
