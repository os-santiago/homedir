package com.scanales.homedir.public_;

import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.reputation.ReputationFeatureFlags;
import com.scanales.homedir.reputation.ReputationHubService;
import com.scanales.homedir.service.GamificationService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.util.AdminUtils;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Optional;

@Path("/comunidad/reputation-hub")
public class ReputationHubResource {

  @Inject SecurityIdentity identity;
  @Inject ReputationFeatureFlags reputationFeatureFlags;
  @Inject ReputationHubService reputationHubService;
  @Inject GamificationService gamificationService;
  @Inject UsageMetricsService metrics;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance hub(ReputationHubService.HubSnapshot hub);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public Response view(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    ReputationFeatureFlags.Flags flags = reputationFeatureFlags.snapshot();
    if (!flags.engineEnabled() || !flags.hubUiEnabled()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    metrics.recordPageView("/comunidad/reputation-hub", headers, context);
    Optional<String> currentUserId = currentUserId();
    currentUserId.ifPresent(
        userId -> gamificationService.award(userId, GamificationActivity.COMMUNITY_BOARD_VIEW));
    String userName = currentUserName().orElse(null);
    boolean authenticated = isAuthenticated();
    boolean admin = AdminUtils.isAdmin(identity);
    boolean showReputationHub = flags.hubUiEnabled() && (admin || flags.hubNavPublicEnabled());
    ReputationHubService.HubSnapshot hub = reputationHubService.snapshot(10);
    TemplateInstance template =
        TemplateLocaleUtil.apply(Templates.hub(hub), localeCookie)
            .data("activePage", "comunidad")
            .data("mainClass", "community-ultra-lite")
            .data("noLoginModal", true)
            .data("activeCommunitySubmenu", "reputation-hub")
            .data("userAuthenticated", authenticated)
            .data("isAdmin", admin)
            .data("showReputationHub", showReputationHub)
            .data("currentUserId", currentUserId.orElse(null))
            .data("userName", userName)
            .data("userInitial", initialFrom(userName));
    return Response.ok(template).build();
  }

  private boolean isAuthenticated() {
    return identity != null && !identity.isAnonymous();
  }

  private Optional<String> currentUserName() {
    if (!isAuthenticated()) {
      return Optional.empty();
    }
    String name = identity.getAttribute("name");
    if (name == null || name.isBlank()) {
      name = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    }
    return Optional.ofNullable(name);
  }

  private Optional<String> currentUserId() {
    if (!isAuthenticated()) {
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

  private String initialFrom(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
  }
}
