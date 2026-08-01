package com.scanales.homedir.public_;

import com.scanales.homedir.achievements.AchievementCatalog;
import com.scanales.homedir.achievements.AchievementCatalog.AchievementGuide;
import com.scanales.homedir.achievements.AchievementProgress;
import com.scanales.homedir.achievements.AchievementProgress.AchievementState;
import com.scanales.homedir.achievements.AchievementService;
import com.scanales.homedir.achievements.AchievementService.LeaderboardEntry;
import com.scanales.homedir.achievements.AchievementView;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.service.UserProfileService;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GitHub Achievement Hub (issue #1043).
 *
 * <p>Displays the achievement catalog with per-user progress tracking, interactive guides, a
 * community leaderboard, and GitHub highlights. Authenticated users can verify their achievements
 * via the GitHub API and earn XP through the gamification system.
 */
@Path("/achievements")
public class AchievementResource {

  @Inject AchievementCatalog catalog;
  @Inject AchievementService achievementService;
  @Inject UserProfileService userProfiles;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance index(
        List<AchievementView> achievements,
        List<AchievementCatalog.OrgRepo> orgRepos,
        int completedCount,
        int totalCount,
        List<LeaderboardEntry> leaderboard,
        boolean userAuthenticated,
        String githubLogin);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance index(
      @CookieParam("QP_LOCALE") String localeCookie,
      @CookieParam("QP_USER_ID") String userIdCookie) {
    AchievementProgress progress = null;
    boolean authenticated = false;
    String githubLogin = null;

    if (userIdCookie != null && !userIdCookie.isBlank()) {
      Optional<UserProfile> profile = userProfiles.find(userIdCookie);
      if (profile.isPresent() && profile.get().getGithub() != null) {
        authenticated = true;
        githubLogin = profile.get().getGithub().login();
        progress = achievementService.getProgress(userIdCookie);
      }
    }

    List<AchievementView> views = new ArrayList<>();
    int completed = 0;
    for (AchievementGuide guide : catalog.guides()) {
      AchievementState state =
          progress != null ? progress.getStates().get(guide.achievement().key()) : null;
      views.add(AchievementView.from(guide, state, localeCookie));
      if (state != null && state.isCompleted()) {
        completed++;
      }
    }

    List<LeaderboardEntry> leaderboard = achievementService.getLeaderboard();

    TemplateInstance template =
        Templates.index(
            views,
            catalog.orgRepos(),
            completed,
            views.size(),
            leaderboard,
            authenticated,
            githubLogin);
    return TemplateLocaleUtil.apply(template, localeCookie).data("activePage", "achievements");
  }

  /**
   * Verifies the authenticated user's achievements via the GitHub API.
   *
   * <p>Redirects back to the achievements page after verification.
   */
  @GET
  @Path("/verify")
  @Produces(MediaType.TEXT_HTML)
  public Response verify(@CookieParam("QP_USER_ID") String userIdCookie) {
    if (userIdCookie == null || userIdCookie.isBlank()) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    Optional<UserProfile> profile = userProfiles.find(userIdCookie);
    if (profile.isEmpty() || profile.get().getGithub() == null) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    String githubLogin = profile.get().getGithub().login();
    achievementService.verifyAchievements(userIdCookie, githubLogin);

    return Response.seeOther(java.net.URI.create("/achievements")).build();
  }

  /** Self-claims an achievement as completed (for achievements without a public API). */
  @GET
  @Path("/claim")
  @Produces(MediaType.TEXT_HTML)
  public Response claim(
      @CookieParam("QP_USER_ID") String userIdCookie,
      @QueryParam("achievement") String achievementKey) {
    if (userIdCookie == null || userIdCookie.isBlank()) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    if (achievementKey == null || achievementKey.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    achievementService.selfClaim(userIdCookie, achievementKey);

    return Response.seeOther(java.net.URI.create("/achievements")).build();
  }
}
