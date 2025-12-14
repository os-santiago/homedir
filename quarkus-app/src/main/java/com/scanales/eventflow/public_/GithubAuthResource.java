package com.scanales.eventflow.public_;

import com.scanales.eventflow.service.GithubService;
import com.scanales.eventflow.service.UserProfileService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.PermitAll;
import java.net.URI;
import org.jboss.logging.Logger;

@Path("/auth/github")
@PermitAll
public class GithubAuthResource {

    private static final Logger LOG = Logger.getLogger(GithubAuthResource.class);

    @Inject
    GithubService githubService;

    @Inject
    UserProfileService userProfileService;

    @GET
    @Path("/callback")
    public Response callback(@QueryParam("code") String code, @QueryParam("state") String state) {
        if (code == null || code.isBlank()) {
            return Response.seeOther(URI.create("/ingresar?error=github_missing_code")).build();
        }

        try {
            String accessToken = githubService.exchangeCode(code);
            GithubService.GithubProfile profile = githubService.fetchUser(accessToken);

            // In a full implementation, we would create a session JWT here.
            // For now, as per request, we "verify" and redirect to community.
            // We can append the github user as a query param to "show" who verified.

            LOG.infov("Verified GitHub user: {0}", profile.login());
            String target = "/comunidad?github_verified=" + profile.login();
            return Response.seeOther(URI.create(target)).build();

        } catch (Exception e) {
            LOG.error("Failed to verify GitHub auth", e);
            return Response.seeOther(URI.create("/ingresar?error=github_verification_failed")).build();
        }
    }
}
