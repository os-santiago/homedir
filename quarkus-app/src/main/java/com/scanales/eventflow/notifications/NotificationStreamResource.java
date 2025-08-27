package com.scanales.eventflow.notifications;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Multi;
import io.vertx.core.http.HttpServerResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/api/notifications")
@Authenticated
public class NotificationStreamResource {

  @Inject SecurityIdentity identity;
  @Inject NotificationStreamService streamService;
  @Inject NotificationConfig config;

  @GET
  @Path("/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  public Multi<io.eventflow.notifications.api.NotificationDTO> stream(
      @Context HttpServerResponse response) {
    if (!config.sseEnabled) {
      throw new NotFoundException();
    }
    Object emailAttr = identity.getAttribute("email");
    String user = emailAttr != null ? emailAttr.toString() : null;
    if (user == null && identity.getPrincipal() != null) {
      user = identity.getPrincipal().getName();
    }
    if (user == null) {
      throw new WebApplicationException("user not found", Response.Status.UNAUTHORIZED);
    }
    response.putHeader("Cache-Control", "no-store");
    response.putHeader("X-User-Scoped", "true");
    return streamService.subscribe(user);
  }
}
