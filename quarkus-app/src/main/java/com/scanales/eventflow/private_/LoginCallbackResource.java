package com.scanales.eventflow.private_;

import com.scanales.eventflow.security.RedirectSanitizer;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UserProfileService;
import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.net.URI;

/** Redirects authenticated users back to a requested page after login. */
@Path("/private/login-callback")
public class LoginCallbackResource {

  @Inject SecurityIdentity identity;

  @Inject UserProfileService userProfileService;
  @Inject GamificationService gamificationService;
  @Inject UsageMetricsService metrics;

  @GET
  @Authenticated
  public Response callback(@QueryParam("redirect") String redirect) {
    String safeRedirect = RedirectSanitizer.sanitizeInternalRedirect(redirect, "/");
    metrics.recordFunnelStep("auth.login.callback");
    metrics.recordFunnelStep("login_success");

    try {
      String userId = currentUserId();
      if (userId != null) {
        if (identity.getPrincipal() instanceof OidcJwtCallerPrincipal jwt) {
          String email = jwt.getClaim("email");
          String name = jwt.getClaim("name");
          if (email != null) {
            userProfileService.upsert(userId, name, email);
          }
        }
        gamificationService.award(userId, GamificationActivity.FIRST_LOGIN_BONUS);
        gamificationService.award(userId, GamificationActivity.DAILY_CHECKIN);
      }
    } catch (Exception e) {
      // Do not block login on profile progression updates.
    }

    return Response.seeOther(URI.create(safeRedirect)).build();
  }

  private String currentUserId() {
    if (identity == null || identity.isAnonymous()) {
      return null;
    }
    String email = null;
    if (identity.getPrincipal() instanceof OidcJwtCallerPrincipal jwt) {
      email = jwt.getClaim("email");
    }
    if (email != null && !email.isBlank()) {
      return email.toLowerCase();
    }
    return identity.getPrincipal().getName();
  }
}
