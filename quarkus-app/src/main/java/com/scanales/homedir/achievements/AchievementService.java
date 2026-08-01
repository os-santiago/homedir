package com.scanales.homedir.achievements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.homedir.achievements.AchievementCatalog.Achievement;
import com.scanales.homedir.achievements.AchievementCatalog.AchievementGuide;
import com.scanales.homedir.achievements.AchievementCatalog.OrgRepo;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.service.GamificationService;
import com.scanales.homedir.service.UserProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

/**
 * Service for verifying GitHub achievements against the GitHub API and managing the achievement
 * leaderboard.
 *
 * <p>Achievement verification queries the GitHub Search API and Repos API to check whether a user
 * has met the criteria for each achievement (merged PRs, closed issues, starred repos, etc.).
 * Results are cached with a TTL to avoid hitting rate limits.
 */
@ApplicationScoped
public class AchievementService {

  private static final Logger LOG = Logger.getLogger(AchievementService.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration CACHE_TTL = Duration.ofMinutes(30);
  private static final int LEADERBOARD_LIMIT = 10;

  @Inject AchievementCatalog catalog;
  @Inject UserProfileService userProfiles;
  @Inject GamificationService gamificationService;
  @Inject ObjectMapper objectMapper;
  @Inject Config config;

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

  private final ConcurrentHashMap<String, AchievementVerificationCache> verificationCache =
      new ConcurrentHashMap<>();

  public record AchievementStatus(String key, String title, boolean unlocked, int progress,
      int threshold, String category, String docUrl, int xpReward) {}

  public record UserAchievementSnapshot(
      String githubLogin,
      List<AchievementStatus> statuses,
      int unlockedCount,
      int totalCount,
      int totalXpEarned) {}

  public record LeaderboardEntry(
      int rank,
      String userId,
      String displayName,
      String githubLogin,
      String avatarUrl,
      int unlockedCount,
      int totalXp) {}

  public record Leaderboard(List<LeaderboardEntry> entries, int totalParticipants) {}

  public record AchievementVerificationResult(boolean verified, int progress, String message) {}

  private record AchievementVerificationCache(
      List<AchievementStatus> statuses, Instant cachedAt) {
    boolean isExpired() {
      return cachedAt != null && Instant.now().isAfter(cachedAt.plus(CACHE_TTL));
    }
  }

  /**
   * Verifies all achievements for a user by querying the GitHub API.
   *
   * @param githubLogin the user's GitHub login
   * @return a snapshot of achievement statuses
   */
  public UserAchievementSnapshot verifyAchievements(String githubLogin) {
    if (githubLogin == null || githubLogin.isBlank()) {
      return new UserAchievementSnapshot(githubLogin, List.of(), 0, 0, 0);
    }

    AchievementVerificationCache cached = verificationCache.get(githubLogin);
    if (cached != null && !cached.isExpired()) {
      int unlocked = (int) cached.statuses().stream().filter(AchievementStatus::unlocked).count();
      int xp = cached.statuses().stream().filter(AchievementStatus::unlocked)
          .mapToInt(AchievementStatus::xpReward).sum();
      return new UserAchievementSnapshot(githubLogin, cached.statuses(), unlocked,
          cached.statuses().size(), xp);
    }

    List<AchievementStatus> statuses = new ArrayList<>();
    for (AchievementGuide guide : catalog.guides()) {
      Achievement achievement = guide.achievement();
      AchievementVerificationResult result = verifySingleAchievement(githubLogin, achievement);
      statuses.add(new AchievementStatus(
          achievement.key(), achievement.title(), result.verified(),
          result.progress(), achievement.threshold(), achievement.category(),
          achievement.docUrl(), achievement.xpReward()));
    }

    verificationCache.put(githubLogin, new AchievementVerificationCache(statuses, Instant.now()));

    int unlocked = (int) statuses.stream().filter(AchievementStatus::unlocked).count();
    int xp = statuses.stream().filter(AchievementStatus::unlocked)
        .mapToInt(AchievementStatus::xpReward).sum();

    return new UserAchievementSnapshot(githubLogin, statuses, unlocked, statuses.size(), xp);
  }

  /**
   * Verifies a single achievement by querying the GitHub API.
   * Uses the GitHub Search API to count merged PRs, closed issues, etc.
   */
  public AchievementVerificationResult verifySingleAchievement(String login, Achievement achievement) {
    String token = getGithubApiToken();
    try {
      int progress = switch (achievement.key()) {
        case "pull-shark" -> countSearchResults(
            "author:" + login + " type:pr is:merged org:os-santiago", token);
        case "yolo" -> countSearchResults(
            "author:" + login + " type:pr is:merged is:unmerged org:os-santiago", token);
        case "quickdraw" -> countSearchResults(
            "author:" + login + " type:issue closed:>=2024-01-01 org:os-santiago", token);
        case "pair-extraordinaire" -> countSearchResults(
            "co-authored-by:" + login + " type:pr is:merged org:os-santiago", token);
        case "starstruck" -> countStarredRepos(login, token);
        case "galaxy-brain" -> 0;
        case "public-sponsor" -> countSponsorships(login, token);
        case "heart-on-your-sleeve" -> countSponsorships(login, token);
        case "open-sourcerer" -> countSponsorships(login, token);
        default -> 0;
      };

      boolean verified = progress >= achievement.threshold();
      String message = verified ? "Achievement unlocked!" : "Progress: " + progress + "/"
          + achievement.threshold();
      return new AchievementVerificationResult(verified, progress, message);
    } catch (Exception e) {
      LOG.warnf(e, "Failed to verify achievement %s for %s", achievement.key(), login);
      return new AchievementVerificationResult(false, 0, "Verification unavailable");
    }
  }

  /**
   * Counts GitHub Search API results for a given query.
   */
  int countSearchResults(String query, String token) {
    try {
      String url = "https://api.github.com/search/issues?q=" + urlEncode(query) + "&per_page=1";
      HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(REQUEST_TIMEOUT)
          .header("Accept", "application/vnd.github+json")
          .header("X-GitHub-Api-Version", "2022-11-28")
          .header("User-Agent", "homedir-achievements");
      if (token != null && !token.isBlank()) {
        builder.header("Authorization", "Bearer " + token);
      }
      HttpResponse<String> response = httpClient.send(builder.build(),
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        LOG.warnf("GitHub search failed status=%d query=%s", response.statusCode(), query);
        return 0;
      }
      JsonNode json = objectMapper.readTree(response.body());
      return Math.max(0, json.path("total_count").asInt(0));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return 0;
    } catch (Exception e) {
      LOG.warnf(e, "GitHub search failed query=%s", query);
      return 0;
    }
  }

  /**
   * Counts the number of starred repositories for a user.
   */
  int countStarredRepos(String login, String token) {
    try {
      String url = "https://api.github.com/users/" + urlEncode(login) + "/starred?per_page=1";
      HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(REQUEST_TIMEOUT)
          .header("Accept", "application/vnd.github+json")
          .header("X-GitHub-Api-Version", "2022-11-28")
          .header("User-Agent", "homedir-achievements");
      if (token != null && !token.isBlank()) {
        builder.header("Authorization", "Bearer " + token);
      }
      HttpResponse<String> response = httpClient.send(builder.build(),
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        return 0;
      }
      // Parse the List header which contains pagination info
      String linkHeader = response.headers().firstValue("link").orElse("");
      if (linkHeader.contains("page=")) {
        // Extract last page number
        String[] parts = linkHeader.split(",");
        for (String part : parts) {
          if (part.contains("rel=\"last\"")) {
            int idx = part.indexOf("page=") + 6;
            int end = part.indexOf("&", idx);
            if (end == -1) end = part.indexOf(">", idx);
            if (end == -1) end = part.length();
            return Integer.parseInt(part.substring(idx, end).trim());
          }
        }
      }
      // No pagination header — count the array
      JsonNode json = objectMapper.readTree(response.body());
      return json.isArray() ? json.size() : 0;
    } catch (Exception e) {
      LOG.warnf(e, "Failed to count starred repos for %s", login);
      return 0;
    }
  }

  /**
   * Counts public sponsorships. GitHub doesn't expose this via a public API,
   * so we return 0 (verification not available for sponsor-based achievements).
   */
  int countSponsorships(String login, String token) {
    return 0;
  }

  /**
   * Awards XP to a user for a verified achievement.
   *
   * @param userId the HomeDir user ID
   * @param achievementKey the achievement key (e.g. "pull-shark")
   * @return true if XP was awarded, false if already awarded or verification failed
   */
  public boolean awardAchievementXp(String userId, String achievementKey) {
    if (userId == null || userId.isBlank() || achievementKey == null) {
      return false;
    }
    AchievementGuide guide = catalog.guideForKey(achievementKey);
    if (guide == null) {
      return false;
    }
    UserProfile profile = userProfiles.find(userId).orElse(null);
    if (profile == null || !profile.hasGithub()) {
      return false;
    }
    String githubLogin = profile.getGithub().login();
    AchievementVerificationResult result = verifySingleAchievement(githubLogin, guide.achievement());
    if (!result.verified()) {
      return false;
    }
    GamificationActivity activity = activityForAchievement(achievementKey);
    if (activity == null) {
      return false;
    }
    return gamificationService.award(userId, activity, guide.achievement().title());
  }

  /**
   * Maps an achievement key to a GamificationActivity.
   */
  GamificationActivity activityForAchievement(String achievementKey) {
    return switch (achievementKey) {
      case "pull-shark" -> GamificationActivity.ACHIEVEMENT_PULL_SHARK;
      case "yolo" -> GamificationActivity.ACHIEVEMENT_YOLO;
      case "quickdraw" -> GamificationActivity.ACHIEVEMENT_QUICKDRAW;
      case "pair-extraordinaire" -> GamificationActivity.ACHIEVEMENT_PAIR_EXTRAORDINAIRE;
      case "starstruck" -> GamificationActivity.ACHIEVEMENT_STARSTRUCK;
      case "galaxy-brain" -> GamificationActivity.ACHIEVEMENT_GALAXY_BRAIN;
      case "public-sponsor" -> GamificationActivity.ACHIEVEMENT_PUBLIC_SPONSOR;
      case "heart-on-your-sleeve" -> GamificationActivity.ACHIEVEMENT_HEART_ON_SLEEVE;
      case "open-sourcerer" -> GamificationActivity.ACHIEVEMENT_OPEN_SOURCERER;
      default -> null;
    };
  }

  /**
   * Builds the achievement leaderboard from all user profiles that have GitHub linked.
   * The leaderboard is sorted by unlocked achievement count (descending), then by total XP.
   */
  public Leaderboard buildLeaderboard() {
    List<UserProfile> allProfileList = new ArrayList<>(userProfiles.allProfiles().values());
    List<LeaderboardEntry> entries = new ArrayList<>();

    for (UserProfile profile : allProfileList) {
      if (profile == null || !profile.hasGithub()) {
        continue;
      }
      String githubLogin = profile.getGithub().login();
      AchievementVerificationCache cached = verificationCache.get(githubLogin);
      if (cached == null) {
        continue;
      }
      int unlocked = (int) cached.statuses().stream()
          .filter(AchievementStatus::unlocked).count();
      int xp = cached.statuses().stream()
          .filter(AchievementStatus::unlocked)
          .mapToInt(AchievementStatus::xpReward).sum();
      if (unlocked > 0) {
        entries.add(new LeaderboardEntry(
            0,
            profile.getUserId(),
            profile.getName() != null ? profile.getName() : githubLogin,
            githubLogin,
            profile.getGithub().avatarUrl(),
            unlocked,
            xp));
      }
    }

    entries.sort(Comparator.comparingInt(LeaderboardEntry::unlockedCount).reversed()
        .thenComparingInt(LeaderboardEntry::totalXp).reversed()
        .thenComparing(LeaderboardEntry::githubLogin, Comparator.nullsLast(String::compareTo)));

    // Assign ranks
    List<LeaderboardEntry> ranked = new ArrayList<>();
    for (int i = 0; i < Math.min(entries.size(), LEADERBOARD_LIMIT); i++) {
      LeaderboardEntry e = entries.get(i);
      ranked.add(new LeaderboardEntry(
          i + 1, e.userId(), e.displayName(), e.githubLogin(), e.avatarUrl(),
          e.unlockedCount(), e.totalXp()));
    }

    return new Leaderboard(ranked, entries.size());
  }

  /**
   * Returns the full catalog data for rendering the page.
   */
  public AchievementCatalog catalog() {
    return catalog;
  }

  /**
   * Returns the list of GitHub highlights (badges that appear on the profile).
   */
  public List<Highlight> highlights() {
    return List.of(
        new Highlight("pro", "GitHub Pro",
            "GitHub Pro is a paid subscription that gives you access to private repositories, "
                + "advanced insights, and more. It appears as a badge on your profile.",
            "https://docs.github.com/en/get-started/learning-about-github/githubs-products"),
        new Highlight("developer-program", "Developer Program Member",
            "The GitHub Developer Program is for developers who build integrations with the "
                + "GitHub API. Join at developer.github.com to get the badge.",
            "https://docs.github.com/en/developers/overview/github-developer-program"),
        new Highlight("security-bounty", "Security Bug Bounty Hunter",
            "Report security vulnerabilities to GitHub's bug bounty program. Accepted reports "
                + "earn a special badge on your profile.",
            "https://bounty.github.com/"),
        new Highlight("galaxy-brain-highlight", "Galaxy Brain (Discussions)",
            "Answer questions in GitHub Discussions. When your answer is accepted by the "
                + "question author, you earn the Galaxy Brain badge.",
            "https://docs.github.com/en/discussions"));
  }

  public record Highlight(String key, String title, String description, String docUrl) {}

  private String getGithubApiToken() {
    return config.getOptionalValue("GH_TOKEN", String.class).orElse("").trim();
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
