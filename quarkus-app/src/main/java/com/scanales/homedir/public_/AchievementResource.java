package com.scanales.homedir.public_;

import com.scanales.homedir.achievements.AchievementCatalog;
import com.scanales.homedir.achievements.AchievementService;
import com.scanales.homedir.achievements.AchievementService.Leaderboard;
import com.scanales.homedir.achievements.AchievementService.UserAchievementSnapshot;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.service.UserProfileService;
import com.scanales.homedir.util.AdminUtils;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jboss.logging.Logger;

@Path("/achievements")
public class AchievementResource {

  private static final Logger LOG = Logger.getLogger(AchievementResource.class);

  @Inject SecurityIdentity identity;
  @Inject AchievementService achievementService;
  @Inject AchievementCatalog catalog;
  @Inject UserProfileService userProfileService;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance index(
        List<AchievementCatalog.AchievementGuide> guides,
        List<AchievementCatalog.OrgRepo> orgRepos,
        List<AchievementService.Highlight> highlights,
        Leaderboard leaderboard,
        UserAchievementSnapshot userSnapshot,
        boolean userAuthenticated,
        boolean githubLinked);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance index(@CookieParam("QP_LOCALE") String localeCookie) {
    boolean authenticated = isAuthenticated();
    Optional<String> userId = currentUserId();
    boolean githubLinked = false;
    UserAchievementSnapshot userSnapshot = null;

    if (userId.isPresent()) {
      UserProfile profile = userProfileService.find(userId.get()).orElse(null);
      if (profile != null && profile.hasGithub()) {
        githubLinked = true;
        try {
          userSnapshot = achievementService.verifyAchievements(profile.getGithub().login());
        } catch (Exception e) {
          LOG.warnf(e, "Failed to verify achievements for user %s", userId.get());
        }
      }
    }

    Leaderboard leaderboard = achievementService.buildLeaderboard();
    List<AchievementService.Highlight> highlights = achievementService.highlights();

    String userName = currentUserName().orElse(null);

    return TemplateLocaleUtil.apply(
            Templates.index(
                catalog.guides(),
                catalog.orgRepos(),
                highlights,
                leaderboard,
                userSnapshot,
                authenticated,
                githubLinked),
            localeCookie)
        .data("activePage", "achievements")
        .data("userAuthenticated", authenticated)
        .data("userName", userName)
        .data("userInitial", initialFrom(userName));
  }

  private boolean isAuthenticated() {
    try {
      return identity != null && !identity.isAnonymous();
    } catch (Exception e) {
      LOG.warn("Security identity check failed: " + e.getMessage());
      return false;
    }
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
