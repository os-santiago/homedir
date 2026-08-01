package com.scanales.homedir.public_;

import com.scanales.homedir.achievements.AchievementService;
import com.scanales.homedir.achievements.AchievementService.AchievementVerificationResult;
import com.scanales.homedir.config.AppMessages;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.service.UserProfileService;
import com.scanales.homedir.util.AdminUtils;
import io.quarkus.qute.i18n.MessageBundles;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Optional;

@Path("/api/achievements")
public class AchievementApiResource {

  @Inject SecurityIdentity identity;
  @Inject AchievementService achievementService;
  @Inject UserProfileService userProfileService;

  @GET
  @Path("/verify/{achievementKey}")
  @RolesAllowed("user")
  @Produces(MediaType.APPLICATION_JSON)
  public Response verifyAchievement(@PathParam("achievementKey") String achievementKey) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    UserProfile profile = userProfileService.find(userId.get()).orElse(null);
    if (profile == null || !profile.hasGithub()) {
      return error(localized().achievements_api_no_github());
    }
    var guide = achievementService.catalog().guideForKey(achievementKey);
    if (guide == null) {
      return error(localized().achievements_api_not_found());
    }
    // Serve from the cached per-user snapshot to avoid bypassing the 30-minute cache.
    AchievementVerificationResult result =
        achievementService.verifySingleAchievementCached(
            profile.getGithub().login(), guide.achievement());
    return Response.ok()
        .entity(
            "{\"verified\":"
                + result.verified()
                + ",\"progress\":"
                + result.progress()
                + ",\"threshold\":"
                + guide.achievement().threshold()
                + ",\"message\":\""
                + result.message().replace("\"", "\\\"")
                + "\"}")
        .build();
  }

  @POST
  @Path("/claim/{achievementKey}")
  @RolesAllowed("user")
  @Produces(MediaType.APPLICATION_JSON)
  public Response claimAchievement(@PathParam("achievementKey") String achievementKey) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    boolean awarded = achievementService.awardAchievementXp(userId.get(), achievementKey);
    if (awarded) {
      return Response.ok()
          .entity(
              "{\"awarded\":true,\"message\":\""
                  + localized().achievements_api_award_success().replace("\"", "\\\"")
                  + "\"}")
          .build();
    }
    return Response.ok()
        .entity(
            "{\"awarded\":false,\"message\":\""
                + localized().achievements_api_award_failure().replace("\"", "\\\"")
                + "\"}")
        .build();
  }

  private AppMessages localized() {
    return MessageBundles.get(AppMessages.class);
  }

  private Response error(String message) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}")
        .build();
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
