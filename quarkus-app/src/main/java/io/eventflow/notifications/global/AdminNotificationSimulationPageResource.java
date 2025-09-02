package io.eventflow.notifications.global;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Admin page to simulate notifications. */
@Path("/admin/notifications/sim")
public class AdminNotificationSimulationPageResource {

  @CheckedTemplate(basePath = "admin")
  static class Templates {
    static native TemplateInstance notifications_sim();
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @RolesAllowed("admin")
  public TemplateInstance page() {
    return Templates.notifications_sim();
  }
}
