package com.scanales.eventflow.notifications;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/api/notifications")
@Authenticated
public class NotificationPollingResource {

  @Inject SecurityIdentity identity;
  @Inject NotificationService notifications;
  @Inject NotificationConfig config;

  @GET
  @Path("/next")
  @Produces(MediaType.APPLICATION_JSON)
  public Response next(@QueryParam("since") long since, @QueryParam("limit") Integer limit) {
    Object emailAttr = identity.getAttribute("email");
    String user = emailAttr != null ? emailAttr.toString() : null;
    if (user == null && identity.getPrincipal() != null) {
      user = identity.getPrincipal().getName();
    }
    if (user == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    int lim = limit == null ? config.pollLimit : Math.min(limit, config.pollLimit);
    List<Notification> list = notifications.listForUser(user, 1000, false);
    List<Notification> filtered =
        list.stream()
            .filter(n -> n.createdAt > since)
            .sorted((a, b) -> Long.compare(a.createdAt, b.createdAt))
            .limit(lim)
            .toList();
    long nextSince = filtered.stream().mapToLong(n -> n.createdAt).max().orElse(since);
    List<io.eventflow.notifications.api.NotificationDTO> items =
        filtered.stream().map(this::toDTO).toList();
    Map<String, Object> body =
        Map.of("items", items, "serverTime", System.currentTimeMillis(), "nextSince", nextSince);
    return Response.ok(body)
        .header("Cache-Control", "no-store")
        .header("X-User-Scoped", "true")
        .build();
  }

  private io.eventflow.notifications.api.NotificationDTO toDTO(Notification n) {
    io.eventflow.notifications.api.NotificationDTO dto =
        new io.eventflow.notifications.api.NotificationDTO();
    dto.id = n.id;
    dto.talkId = n.talkId;
    dto.eventId = n.eventId;
    dto.type = n.type != null ? n.type.name() : null;
    dto.title = n.title;
    dto.message = n.message;
    dto.createdAt = n.createdAt;
    return dto;
  }
}
