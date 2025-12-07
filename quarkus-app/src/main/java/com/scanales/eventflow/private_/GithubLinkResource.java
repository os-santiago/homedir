package com.scanales.eventflow.private_;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/private/github")
public class GithubLinkResource {

  @Inject
  SecurityIdentity identity;

  @Inject
  GithubLinkService service;

  @GET
  @Path("start")
  @Produces(MediaType.TEXT_HTML)
  public Response start(@QueryParam("redirect") String redirect) {
    return service.start(identity, redirect);
  }

  @GET
  @Path("callback")
  @Produces(MediaType.TEXT_HTML)
  public Response callback(
      @QueryParam("code") String code,
      @QueryParam("state") String state,
      @QueryParam("error") String error,
      @CookieParam("gh_state") Cookie stateCookie,
      @CookieParam("gh_redirect") Cookie redirectCookie) {
    return service.handleCallback(code, state, error, stateCookie, redirectCookie, identity);
  }
}
