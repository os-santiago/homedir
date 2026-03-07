package io.eventflow.notifications.rest;

import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.util.AdminUtils;
import com.scanales.eventflow.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
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
  public TemplateInstance center(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context HttpHeaders headers) {
    currentUserId()
        .ifPresent(userId -> gamificationService.award(userId, GamificationActivity.NOTIFICATIONS_CENTER_VIEW));
    boolean authenticated = identity != null && !identity.isAnonymous();
    String userName = authenticated && identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    return TemplateLocaleUtil.apply(Templates.center(), localeCookie, headers)
        .data("activePage", "notifications")
        .data("noLoginModal", true)
        .data("userAuthenticated", authenticated)
        .data("userName", userName)
        .data("userInitial", initialFrom(userName));
  }

  private static String initialFrom(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
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
