package io.eventflow.notifications.global;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Admin page for broadcasting global notifications. */
@Path("/admin/notifications")
public class AdminNotificationPageResource {

  @CheckedTemplate(basePath = "admin")
  static class Templates {
    static native TemplateInstance notifications();
  }

  @jakarta.inject.Inject
  com.scanales.eventflow.config.AppMessages appMessages;

  @GET
  @Produces(MediaType.TEXT_HTML)
  @RolesAllowed("admin")
  public TemplateInstance page(@jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie) {
    String lang = "es";
    if (localeCookie != null && !localeCookie.isBlank()) {
      lang = localeCookie;
    }
    return Templates.notifications()
        .data("locale", java.util.Locale.forLanguageTag(lang))
        .data("currentLanguage", lang);
  }
}
