package com.scanales.eventflow.private_;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/private/admin/events/{id}/cfp")
public class AdminEventCfpResource {

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance moderation(Event event);
  }

  @Inject EventService eventService;

  @Inject SecurityIdentity identity;

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response moderation(@PathParam("id") String eventId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    Event event = eventService.getEvent(eventId);
    if (event == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(Templates.moderation(event)).build();
  }
}
