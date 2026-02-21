package io.eventflow.notifications.rest;

import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Locale;
import java.util.Optional;

/** Public page displaying global notifications. */
@Path("/notifications")
public class NotificationPageResource {

  @Inject SecurityIdentity identity;
  @Inject GamificationService gamificationService;

  @CheckedTemplate(basePath = "notifications")
  static class Templates {
    static native TemplateInstance center();
  }

  @GET
  @Path("/center")
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance center() {
    currentUserId()
        .ifPresent(userId -> gamificationService.award(userId, GamificationActivity.NOTIFICATIONS_CENTER_VIEW));
    return Templates.center();
  }

  private Optional<String> currentUserId() {
    if (identity == null || identity.isAnonymous()) {
      return Optional.empty();
    }
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return Optional.of(email.toLowerCase(Locale.ROOT));
    }
    String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    if (principal == null || principal.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(principal.toLowerCase(Locale.ROOT));
  }
}
