package com.scanales.eventflow.public_;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.PermitAll;

@Path("/auth/github")
@PermitAll
public class GithubAuthResource {

    @Inject
    io.quarkus.security.identity.SecurityIdentity identity;

    @Inject
    com.scanales.eventflow.private_.GithubLinkService linkService;

    @GET
    @Path("/callback")
    public Response callback(
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error,
            @jakarta.ws.rs.CookieParam("gh_state") jakarta.ws.rs.core.Cookie stateCookie,
            @jakarta.ws.rs.CookieParam("gh_redirect") jakarta.ws.rs.core.Cookie redirectCookie) {

        return linkService.handleCallback(code, state, error, stateCookie, redirectCookie, identity);
    }
}
