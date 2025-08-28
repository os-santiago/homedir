package io.eventflow.notifications.rest;

import com.scanales.eventflow.notifications.NotificationService;
import io.eventflow.notifications.api.NotificationListResponse;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/notifications")
@Authenticated
public class NotificationPageResource {

  @CheckedTemplate(basePath = "notifications")
  static class Templates {
    static native TemplateInstance center(NotificationListResponse data);
  }

  @Inject NotificationService service;
  @Inject SecurityIdentity identity;

  private String userId() {
    return SecurityIdentityUser.id(identity);
  }

  @GET
  @Path("/center")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance center() {
    String user = userId();
    if (user == null) {
      throw new jakarta.ws.rs.WebApplicationException(
          "user not found", jakarta.ws.rs.core.Response.Status.UNAUTHORIZED);
    }
    var page = service.listPage(user, "all", null, 20);
    return Templates.center(NotificationListResponse.from(page));
  }
}
