package com.scanales.eventflow.capacity;

import com.scanales.eventflow.service.CapacityService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/** Blocks new logins when memory or disk capacity is low. */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class CapacityLoginFilter implements ContainerRequestFilter {

  private static final String MESSAGE =
      "Debido a alta demanda, no podemos gestionar tus datos en este momento. Inténtalo más tarde.";

  @Inject SecurityIdentity identity;

  @Inject CapacityService capacity;

  @Override
  public void filter(ContainerRequestContext ctx) {
    if (identity == null || identity.isAnonymous()) {
      return;
    }
    String path = ctx.getUriInfo().getPath();
    if (!path.startsWith("private")) {
      return;
    }
    String email = getEmail();
    if (!capacity.isAdmitted(email)) {
      CapacityService.Status status = capacity.evaluate();
      if (status.mode() == CapacityService.Mode.CONTAINING) {
        ctx.abortWith(
            Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(MESSAGE)
                .type(MediaType.TEXT_PLAIN)
                .build());
        return;
      }
      capacity.markAdmitted(email);
    }
  }

  private String getEmail() {
    Object value = identity.getAttribute("email");
    if (value == null) {
      value = identity.getPrincipal().getName();
    }
    return value == null ? null : value.toString();
  }
}
