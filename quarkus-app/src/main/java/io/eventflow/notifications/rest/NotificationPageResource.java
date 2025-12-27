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

  @jakarta.inject.Inject
  com.scanales.eventflow.config.AppMessages appMessages;

  @GET
  @Path("/center")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance center(@jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie) {
    String lang = "es";
    if (localeCookie != null && !localeCookie.isBlank()) {
      lang = localeCookie;
    }
    return Templates.center()
        .data("locale", java.util.Locale.forLanguageTag(lang))
        .data("currentLanguage", lang);
  }
}
