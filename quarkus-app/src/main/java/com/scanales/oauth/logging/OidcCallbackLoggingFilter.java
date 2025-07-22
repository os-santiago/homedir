package com.scanales.oauth.logging;

import java.io.IOException;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Logs all parameters received on the OIDC callback endpoint.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class OidcCallbackLoggingFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(OidcCallbackLoggingFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        if (path.startsWith("q/oauth2/callback") || path.startsWith("q/oidc/callback")) {
            String params = requestContext.getUriInfo().getQueryParameters().entrySet()
                    .stream()
                    .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
                    .collect(Collectors.joining(", "));
            LOG.infov("OAuth callback parameters: {0}", params);
        }
    }
}

