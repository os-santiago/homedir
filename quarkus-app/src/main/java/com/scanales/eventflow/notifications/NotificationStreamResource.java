package com.scanales.eventflow.notifications;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/api/notifications")
public class NotificationStreamResource {

  @Inject SecurityIdentity identity;
  @Inject NotificationStreamService streamService;
  @Inject NotificationConfig config;

  @GET
  @Path("/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  public Multi<io.eventflow.notifications.api.NotificationDTO> stream() {
    if (!config.sseEnabled) {
      throw new NotFoundException();
    }
    String user = identity.getAttribute("email");
    if (user == null && identity.getPrincipal() != null) {
      user = identity.getPrincipal().getName();
    }
    return streamService.subscribe(user);
  }
}
