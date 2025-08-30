package io.eventflow.notifications.global;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

@Path("/admin/api/notifications")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin"})
public class AdminNotificationResource {

  @Inject GlobalNotificationService service;

  @POST
  @Path("/broadcast")
  public Response broadcast(GlobalNotification dto) {
    if (dto == null || dto.title == null || dto.message == null) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    dto.id = dto.id != null ? dto.id : UUID.randomUUID().toString();
    if (dto.createdAt == 0) {
      dto.createdAt = System.currentTimeMillis();
    }
    if (dto.dedupeKey == null) {
      dto.dedupeKey = dto.type + "|" + (dto.eventId == null ? "" : dto.eventId);
    }
    service.enqueue(dto);
    return Response.ok(Map.of("id", dto.id)).build();
  }

  @GET
  @Path("/latest")
  public Response latest(@QueryParam("limit") @DefaultValue("50") int limit) {
    return Response.ok(service.latest(limit)).build();
  }

  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") String id) {
    boolean removed = service.removeById(id);
    return removed ? Response.noContent().build() : Response.status(404).build();
  }
}
