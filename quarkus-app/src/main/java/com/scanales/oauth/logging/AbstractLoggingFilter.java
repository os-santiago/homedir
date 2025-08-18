package com.scanales.oauth.logging;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import java.io.IOException;
import org.jboss.logging.Logger;

/**
 * Base logging filter with a per-class logger and a single entry point delegating to subclasses.
 */
public abstract class AbstractLoggingFilter implements ContainerRequestFilter {

  protected final Logger log = Logger.getLogger(getClass());

  @Override
  public final void filter(ContainerRequestContext requestContext) throws IOException {
    handle(requestContext);
  }

  protected abstract void handle(ContainerRequestContext requestContext) throws IOException;
}
