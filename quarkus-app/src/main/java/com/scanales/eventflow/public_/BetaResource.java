package com.scanales.eventflow.public_;

import com.scanales.eventflow.service.UserSessionService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

@Path("/beta")
public class BetaResource {

  @Inject SecurityIdentity identity;
  @Inject UserSessionService userSessionService;
  @Inject UsageMetricsService metrics;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance beta(boolean isAuthenticated);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance view(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context HttpHeaders headers,
      @jakarta.ws.rs.core.Context RoutingContext context) {
    metrics.recordPageView("/beta", headers, context);
    boolean authenticated = identity != null && !identity.isAnonymous();
    String userName =
        authenticated && identity.getPrincipal() != null ? identity.getPrincipal().getName() : "";
    return TemplateLocaleUtil.apply(Templates.beta(authenticated), localeCookie, headers)
        .data("activePage", "beta")
        .data("mainClass", "beta-main")
        .data("noLoginModal", true)
        .data("userAuthenticated", authenticated)
        .data("userName", userName)
        .data("userSession", userSessionService.getCurrentSession())
        .data("userInitial", initialFrom(userName));
  }

  private String initialFrom(String name) {
    if (name == null) {
      return "";
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    return trimmed.substring(0, 1).toUpperCase();
  }
}
