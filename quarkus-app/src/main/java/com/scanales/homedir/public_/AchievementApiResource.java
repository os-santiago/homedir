package com.scanales.homedir.public_;

import com.scanales.homedir.achievements.AchievementService;
import com.scanales.homedir.achievements.AchievementService.AchievementVerificationResult;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.service.UserProfileService;
import com.scanales.homedir.util.AdminUtils;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Optional;
import org.jboss.logging.Logger;

@Path("/api/achievements")
public class AchievementApiResource {

  private static final Logger LOG = Logger.getLogger(AchievementApiResource.class);

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
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"GitHub account not linked\"}")
          .build();
    }
    var guide = achievementService.catalog().guideForKey(achievementKey);
    if (guide == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("{\"error\":\"Achievement not found\"}")
          .build();
    }
    AchievementVerificationResult result =
        achievementService.verifySingleAchievement(
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

  @GET
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
          .entity("{\"awarded\":true,\"message\":\"XP awarded successfully\"}")
          .build();
    }
    return Response.ok()
        .entity("{\"awarded\":false,\"message\":\"Achievement not verified or already claimed\"}")
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
