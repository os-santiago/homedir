package com.scanales.homedir.public_;

import com.scanales.homedir.achievements.AchievementCatalog;
import com.scanales.homedir.achievements.AchievementProgress;
import com.scanales.homedir.achievements.AchievementService;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.service.UserProfileService;
import com.scanales.homedir.util.AdminUtils;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * GitHub Achievement Hub (issue #1043).
 *
 * <p>Renders a dashboard of GitHub achievements with real per-user progress verified via the GitHub
 * API. Authenticated users with a linked GitHub account see their live progress and can trigger a
 * re-verification; anonymous users see the catalog with all achievements locked.
 */
@Path("/achievements")
public class AchievementResource {

  private static final Logger LOG = Logger.getLogger(AchievementResource.class);

  @Inject SecurityIdentity identity;
  @Inject AchievementCatalog catalog;
  @Inject AchievementService achievementService;
  @Inject UserProfileService userProfiles;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance index(
        List<AchievementCatalog.AchievementGuide> guides,
        List<AchievementCatalog.OrgRepo> orgRepos,
        Map<String, AchievementProgress> progressMap,
        boolean githubLinked,
        String githubLogin,
        List<LeaderboardEntry> leaderboard);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance index(@CookieParam("QP_LOCALE") String localeCookie) {
    boolean authenticated = isAuthenticated();
    String userId = currentUserId().orElse(null);
    Map<String, AchievementProgress> progressMap = new HashMap<>();
    boolean githubLinked = false;
    String githubLogin = null;

    if (authenticated && userId != null) {
      UserProfile profile = userProfiles.find(userId).orElse(null);
      if (profile != null && profile.hasGithub()) {
        githubLinked = true;
        githubLogin = profile.getGithub().login();
        List<AchievementProgress> cached = achievementService.getCachedProgress(githubLogin);
        if (cached != null) {
          for (AchievementProgress p : cached) {
            progressMap.put(p.achievementKey(), p);
          }
        }
      }
    }

    List<LeaderboardEntry> leaderboard = buildLeaderboard();

    TemplateInstance template =
        Templates.index(
            catalog.guides(),
            catalog.orgRepos(),
            progressMap,
            githubLinked,
            githubLogin,
            leaderboard);

    return TemplateLocaleUtil.apply(template, localeCookie)
        .data("activePage", "achievements")
        .data("userAuthenticated", authenticated)
        .data("userName", currentUserName())
        .data("userInitial", initialFrom(currentUserName()));
  }

  /** Triggers a re-verification of the authenticated user's GitHub achievements. */
  @GET
  @Path("/api/verify")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  public Response verify() {
    String userId = currentUserId().orElse(null);
    if (userId == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    UserProfile profile = userProfiles.find(userId).orElse(null);
    if (profile == null || !profile.hasGithub()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "GitHub account not linked"))
          .build();
    }
    String githubLogin = profile.getGithub().login();
    try {
      AchievementService.VerificationResult result = achievementService.verify(githubLogin, userId);
      Map<String, Object> response = new HashMap<>();
      response.put("login", result.githubLogin());
      response.put("newlyCompleted", result.newlyCompleted());
      response.put("progress", result.progress());
      response.put("verifiedAt", result.verifiedAt().toString());
      return Response.ok(response).build();
    } catch (Exception e) {
      LOG.warnf(e, "achievement_verify_endpoint_failed user=%s login=%s", userId, githubLogin);
      return Response.serverError()
          .entity(Map.of("error", "Verification failed: " + e.getMessage()))
          .build();
    }
  }

  // -- Leaderboard ----------------------------------------------------------

  /** A single entry in the achievement leaderboard. */
  public record LeaderboardEntry(
      int rank, String displayName, String githubLogin, String avatarUrl, int completedCount) {}

  private List<LeaderboardEntry> buildLeaderboard() {
    // Build from cached verifications: count COMPLETED achievements per user.
    // This is a best-effort leaderboard based on users who have verified their achievements.
    List<LeaderboardEntry> entries = new java.util.ArrayList<>();
    for (UserProfile profile : userProfiles.allProfiles().values()) {
      if (!profile.hasGithub()) {
        continue;
      }
      String login = profile.getGithub().login();
      List<AchievementProgress> cached = achievementService.getCachedProgress(login);
      if (cached == null) {
        continue;
      }
      int completed =
          (int)
              cached.stream()
                  .filter(p -> p.status() == AchievementProgress.Status.COMPLETED)
                  .count();
      if (completed > 0) {
        entries.add(
            new LeaderboardEntry(
                0,
                profile.getName() != null ? profile.getName() : login,
                login,
                profile.getGithub().avatarUrl(),
                completed));
      }
    }
    entries.sort((a, b) -> Integer.compare(b.completedCount(), a.completedCount()));
    for (int i = 0; i < entries.size(); i++) {
      LeaderboardEntry e = entries.get(i);
      entries.set(
          i,
          new LeaderboardEntry(
              i + 1, e.displayName(), e.githubLogin(), e.avatarUrl(), e.completedCount()));
    }
    return entries.size() > 10 ? entries.subList(0, 10) : entries;
  }

  // -- Auth helpers ---------------------------------------------------------

  private boolean isAuthenticated() {
    try {
      return identity != null && !identity.isAnonymous();
    } catch (Exception e) {
      LOG.warn("Security identity check failed (treating as anonymous): " + e.getMessage());
      return false;
    }
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
    if (principal != null && !principal.isBlank()) {
      return Optional.of(principal.toLowerCase(Locale.ROOT));
    }
    String sub = AdminUtils.getClaim(identity, "sub");
    if (sub != null && !sub.isBlank()) {
      return Optional.of(sub);
    }
    return Optional.empty();
  }

  private String currentUserName() {
    if (!isAuthenticated()) {
      return null;
    }
    String name = identity.getAttribute("name");
    if (name == null || name.isBlank()) {
      name = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    }
    return name;
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
