package io.eventflow.notifications.rest;

import com.scanales.eventflow.notifications.NotificationService;
import io.eventflow.notifications.api.NotificationListResponse;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Set;

@Path("/api/notifications")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

  @Inject NotificationService service;
  @Inject SecurityIdentity identity;

  private Response.ResponseBuilder scoped(Response.ResponseBuilder rb) {
    return rb.header("Cache-Control", "no-store").header("X-User-Scoped", "true");
  }

  private String userId() {
    return SecurityIdentityUser.id(identity);
  }

  private Response unauthorized() {
    return Response.status(Response.Status.UNAUTHORIZED)
        .header("X-Session-Expired", "true")
        .build();
  }

  @GET
  public Response list(
      @QueryParam("filter") String filter,
      @QueryParam("cursor") Long cursor,
      @QueryParam("limit") @DefaultValue("20") @Min(1) @Max(100) int limit) {
    String userId = userId();
    if (userId == null) {
      return unauthorized();
    }
    var page = service.listPage(userId, filter, cursor, limit);
    return scoped(Response.ok(NotificationListResponse.from(page))).build();
  }

  @POST
  @Path("/{id}/read")
  public Response markRead(@PathParam("id") String id) {
    String userId = userId();
    if (userId == null) {
      return unauthorized();
    }
    boolean ok = service.markRead(userId, id);
    if (!ok) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return scoped(Response.noContent()).build();
  }

  @POST
  @Path("/read-all")
  public Response readAll() {
    String userId = userId();
    if (userId == null) {
      return unauthorized();
    }
    service.markAllRead(userId);
    return scoped(Response.noContent()).build();
  }

  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") String id) {
    String userId = userId();
    if (userId == null) {
      return unauthorized();
    }
    boolean ok = service.delete(userId, id);
    if (!ok) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return scoped(Response.noContent()).build();
  }

  @POST
  @Path("/bulk-delete")
  public Response bulkDelete(@Valid BulkDeleteRequest req) {
    String userId = userId();
    if (userId == null) {
      return unauthorized();
    }
    if (req == null || req.ids == null || req.ids.isEmpty() || req.ids.size() > 100) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    service.bulkDelete(userId, Set.copyOf(req.ids));
    return scoped(Response.noContent()).build();
  }

  public static class BulkDeleteRequest {
    public List<String> ids;
  }
}
