package org.acme.oauth.logging;

import java.io.IOException;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.oidc.IdTokenCredential;
import io.quarkus.oidc.AccessTokenCredential;
import org.jboss.logging.Logger;

/**
 * Logs token information and user details after a successful authentication.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 1)
public class PostAuthenticationLoggingFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(PostAuthenticationLoggingFilter.class);

    @Inject
    SecurityIdentity identity;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (identity == null || identity.isAnonymous()) {
            return;
        }

        IdTokenCredential idToken = identity.getCredential(IdTokenCredential.class);
        if (idToken != null) {
            LOG.infov("ID Token: {0}", idToken.getToken());
            String token = idToken.getToken();
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String claimsJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
                LOG.infov("ID Token claims: {0}", claimsJson);
            }
        }

        AccessTokenCredential accessToken = identity.getCredential(AccessTokenCredential.class);
        if (accessToken != null) {
            LOG.infov("Access Token: {0}", accessToken.getToken());
        }

        String email = identity.getAttribute("email");
        String name = identity.getAttribute("name");
        String sub = identity.getPrincipal().getName();
        LOG.infov("Authenticated user: sub={0}, name={1}, email={2}", sub, name, email);
    }
}

