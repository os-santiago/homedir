package com.scanales.eventflow.public_;

import com.scanales.eventflow.private_.DiscordLinkService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;

@Path("/auth/discord")
@PermitAll
public class DiscordAuthResource {

  @Inject SecurityIdentity identity;

  @Inject DiscordLinkService service;

  @GET
  @Path("/callback")
  public Response callback(
      @QueryParam("code") String code,
      @QueryParam("state") String state,
      @QueryParam("error") String error,
      @CookieParam("discord_state") Cookie stateCookie,
      @CookieParam("discord_redirect") Cookie redirectCookie) {
    return service.handleCallback(code, state, error, stateCookie, redirectCookie, identity);
  }
}
