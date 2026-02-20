package com.scanales.eventflow.private_;

import com.scanales.eventflow.security.RedirectSanitizer;
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
  @Inject UsageMetricsService metrics;

  @GET
  @Authenticated
  public Response callback(@QueryParam("redirect") String redirect) {
    String safeRedirect = RedirectSanitizer.sanitizeInternalRedirect(redirect, "/");
    metrics.recordFunnelStep("auth.login.callback");

    // --- Viral Feature: Instant Onboarding XP ---
    // If the user is new (or has 0 XP), give them their first win immediately.
    try {
      String userId = currentUserId();
      if (userId != null) {
        // Find or create profile (ensure it exists)
        // We need basic info. For now, try to find existing or rely on lazy creation
        // elsewhere?
        // Safer to just check find(). logic in Upsert might be needed if it's TRULY
        // their first login ever.
        // But usually profile is created on the first protected page access or via
        // Upsert.
        // Let's just try to find it. If it doesn't exist, we might miss the very first
        // firing here
        // unless we init it. Let's stick to 'find' to avoid bare upserts without
        // name/email data here.
        // Actually, we can just call addXp, which does a find check internally!
        // Wait, addXp returns null if user not found.

        var profileOpt = userProfileService.find(userId);
        if (profileOpt.isPresent()) {
          var profile = profileOpt.get();
          if (profile.getCurrentXp() == 0
              && (profile.getHistory() == null || profile.getHistory().isEmpty())) {
            userProfileService.addXp(userId, 100, "Joined the Resistance");
          }
        } else {
          // First time ever? We might need to create the profile.
          // But we don't have name/email easily here without parsing JWT fully.
          // Let's parse JWT to be robust.
          if (identity.getPrincipal() instanceof OidcJwtCallerPrincipal jwt) {
            String email = jwt.getClaim("email");
            String name = jwt.getClaim("name");
            if (email != null) {
              userProfileService.upsert(userId, name, email);
              // Now award!
              userProfileService.addXp(userId, 100, "Joined the Resistance");
            }
          }
        }
      }
    } catch (Exception e) {
      // Don't block login on gamification failure
    }
    // ---------------------------------------------

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
