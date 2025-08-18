package com.scanales.oauth.logging;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.stream.Collectors;

/** Logs all parameters received on the OIDC callback endpoint. */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class OidcCallbackLoggingFilter extends AbstractLoggingFilter {

  @Override
  protected void handle(ContainerRequestContext requestContext) throws IOException {
    String path = requestContext.getUriInfo().getPath();
    if (path.startsWith("q/oauth2/callback") || path.startsWith("q/oidc/callback")) {
      String params =
          requestContext.getUriInfo().getQueryParameters().entrySet().stream()
              .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
              .collect(Collectors.joining(", "));
      log.infov("OAuth callback parameters: {0}", params);
    }
  }
}
