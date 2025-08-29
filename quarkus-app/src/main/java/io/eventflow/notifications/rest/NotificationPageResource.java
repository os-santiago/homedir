package io.eventflow.notifications.rest;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Public page displaying global notifications. */
@Path("/notifications")
public class NotificationPageResource {

  @CheckedTemplate(basePath = "notifications")
  static class Templates {
    static native TemplateInstance center();
  }

  @GET
  @Path("/center")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance center() {
    return Templates.center();
  }
}
