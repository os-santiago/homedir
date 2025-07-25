package com.scanales.oauth.logging;

import java.io.IOException;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Logs the redirect URL when the user is sent to the OIDC authorization endpoint
 * and any error responses produced during authentication.
 */
@Provider
@Priority(Priorities.USER)
public class AuthorizationRedirectLoggingFilter implements ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(AuthorizationRedirectLoggingFilter.class);
    private static final String PREFIX = "[LOGIN] ";

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        if (responseContext.getStatusInfo().getFamily() == Response.Status.Family.REDIRECTION) {
            String location = responseContext.getHeaderString(HttpHeaders.LOCATION);
            if (location != null && !location.isEmpty()) {
                LOG.infov(PREFIX + "Redirecting user to authorization endpoint: {0}", location);
            }
        }
        if (responseContext.getStatus() >= 400) {
            LOG.infov(PREFIX + "Authentication error: status={0}, message={1}",
                    responseContext.getStatus(),
                    responseContext.getStatusInfo().getReasonPhrase());
        }
    }
}

