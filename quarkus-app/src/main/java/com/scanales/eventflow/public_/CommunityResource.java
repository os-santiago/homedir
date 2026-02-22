package com.scanales.eventflow.public_;

import com.scanales.eventflow.community.CommunityBoardService;
import com.scanales.eventflow.community.CommunityContentMedia;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.util.AdminUtils;
import com.scanales.eventflow.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import org.jboss.logging.Logger;

@Path("/comunidad")
public class CommunityResource {
  private static final Logger LOG = Logger.getLogger(CommunityResource.class);

  @Inject SecurityIdentity identity;
  @Inject CommunityBoardService boardService;
  @Inject UsageMetricsService metrics;
  @Inject GamificationService gamificationService;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance community(
        boolean isAuthenticated, String initialView, int homedirUsers, int githubUsers, int discordUsers);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance view(
      @QueryParam("view") String viewParam,
      @QueryParam("filter") String filterParam,
      @QueryParam("media") String mediaParam,
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    return render(viewParam, filterParam, mediaParam, null, localeCookie, headers, context);
  }

  @GET
  @Path("/lta")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance lta(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    return render("featured", "all", CommunityContentMedia.ALL, "lta", localeCookie, headers, context);
  }

  @GET
  @Path("/moderation")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance moderation(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    return render(
        "featured", "all", CommunityContentMedia.ALL, "moderation", localeCookie, headers, context);
  }

  @GET
  @Path("/propose")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance propose(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    return render("featured", "all", CommunityContentMedia.ALL, "propose", localeCookie, headers, context);
  }

  private TemplateInstance render(
      String viewParam,
      String filterParam,
      String mediaParam,
      String forcedSubmenu,
      String localeCookie,
      jakarta.ws.rs.core.HttpHeaders headers,
      io.vertx.ext.web.RoutingContext context) {
    boolean authenticated = isAuthenticated();
    boolean isAdmin = AdminUtils.isAdmin(identity);
    String initialView = normalizeView(viewParam);
    String initialFilter = normalizeFilter(filterParam);
    String initialMedia = CommunityContentMedia.normalizeFilter(mediaParam);
    String activeSubmenu =
        forcedSubmenu != null && !forcedSubmenu.isBlank() ? forcedSubmenu : "lta";
    metrics.recordPageView("/comunidad/" + activeSubmenu, headers, context);
    currentUserId().ifPresent(
        userId -> {
          if ("lta".equals(activeSubmenu)) {
            gamificationService.award(userId, GamificationActivity.LTA_VIEW);
          } else {
            gamificationService.award(userId, GamificationActivity.COMMUNITY_MAIN_VIEW);
          }
          if ("picks".equals(activeSubmenu)) {
            gamificationService.award(userId, GamificationActivity.COMMUNITY_PICKS_VIEW);
          } else if ("propose".equals(activeSubmenu) || "moderation".equals(activeSubmenu)) {
            gamificationService.award(userId, GamificationActivity.COMMUNITY_PROPOSE_VIEW);
          }
        });
    var summary = boardService.summary();
    TemplateInstance template =
        Templates.community(
            authenticated,
            initialView,
            summary.homedirUsers(),
            summary.githubUsers(),
            summary.discordUsers());
    return TemplateLocaleUtil.apply(template, localeCookie)
        .data("activePage", "comunidad")
        .data("mainClass", "community-ultra-lite")
        .data("activeCommunitySubmenu", activeSubmenu)
        .data("initialFilter", initialFilter)
        .data("initialMedia", initialMedia)
        .data("userAuthenticated", authenticated)
        .data("isAdmin", isAdmin)
        .data("userName", currentUserName().orElse(null))
        .data("userInitial", initialFrom(currentUserName().orElse(null)));
  }

  @GET
  @Path("/feed")
  @PermitAll
  public Response feed() {
    return Response.seeOther(URI.create("/comunidad/propose")).build();
  }

  @GET
  @Path("/picks")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance picks(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    return render("featured", "all", CommunityContentMedia.ALL, "picks", localeCookie, headers, context);
  }

  private String normalizeFilter(String filterParam) {
    if (filterParam == null || filterParam.isBlank()) {
      return "all";
    }
    String normalized = filterParam.trim().toLowerCase();
    if ("all".equals(normalized) || "internet".equals(normalized) || "members".equals(normalized)) {
      return normalized;
    }
    return "all";
  }

  private String normalizeView(String viewParam) {
    if (viewParam == null || viewParam.isBlank()) {
      return "featured";
    }
    String normalized = viewParam.trim().toLowerCase();
    if ("featured".equals(normalized) || "new".equals(normalized)) {
      return normalized;
    }
    return "featured";
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

  private boolean isAuthenticated() {
    try {
      return identity != null && !identity.isAnonymous();
    } catch (Exception e) {
      LOG.warn("Security identity check failed (treating as anonymous): " + e.getMessage());
      return false;
    }
  }

  private String initialFrom(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.substring(0, 1).toUpperCase();
  }
}
