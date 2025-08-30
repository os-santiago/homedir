package io.eventflow.notifications.rest;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/api/notifications")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

  private Response gone() {
    return Response.status(Response.Status.GONE)
        .entity(
            Map.of(
                "error", "notifications-now-global",
                "hint", "use WS /ws/global-notifications"))
        .build();
  }

  @GET
  public Response get() {
    return gone();
  }

  @POST
  @Path("{any:.*}")
  public Response post() {
    return gone();
  }

  @DELETE
  @Path("{any:.*}")
  public Response delete() {
    return gone();
  }

  @PUT
  @Path("{any:.*}")
  public Response put() {
    return gone();
  }

  @OPTIONS
  @Path("{any:.*}")
  public Response options() {
    return gone();
  }
}
