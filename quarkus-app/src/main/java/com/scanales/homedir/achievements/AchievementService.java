package com.scanales.homedir.achievements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.homedir.achievements.AchievementCatalog.Achievement;
import com.scanales.homedir.achievements.AchievementCatalog.AchievementGuide;
import com.scanales.homedir.config.AppMessages;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.service.GamificationService;
import com.scanales.homedir.service.UserProfileService;
import io.quarkus.qute.i18n.MessageBundles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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

  public record AchievementStatus(
      String key,
      String title,
      boolean unlocked,
      int progress,
      int threshold,
      String category,
      String docUrl,
      int xpReward) {}

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

  private record AchievementVerificationCache(List<AchievementStatus> statuses, Instant cachedAt) {
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
      int xp =
          cached.statuses().stream()
              .filter(AchievementStatus::unlocked)
              .mapToInt(AchievementStatus::xpReward)
              .sum();
      return new UserAchievementSnapshot(
          githubLogin, cached.statuses(), unlocked, cached.statuses().size(), xp);
    }

    List<AchievementStatus> statuses = new ArrayList<>();
    for (AchievementGuide guide : catalog.guides()) {
      Achievement achievement = guide.achievement();
      AchievementVerificationResult result = verifySingleAchievement(githubLogin, achievement);
      statuses.add(
          new AchievementStatus(
              achievement.key(),
              achievement.title(),
              result.verified(),
              result.progress(),
              achievement.threshold(),
              achievement.category(),
              achievement.docUrl(),
              achievement.xpReward()));
    }

    verificationCache.put(githubLogin, new AchievementVerificationCache(statuses, Instant.now()));

    int unlocked = (int) statuses.stream().filter(AchievementStatus::unlocked).count();
    int xp =
        statuses.stream()
            .filter(AchievementStatus::unlocked)
            .mapToInt(AchievementStatus::xpReward)
            .sum();

    return new UserAchievementSnapshot(githubLogin, statuses, unlocked, statuses.size(), xp);
  }

  /**
   * Verifies a single achievement, populating the per-user cache on a cache miss. This is the safe
   * entry point for request-triggered verification (verify/claim endpoints, XP awarding): repeated
   * calls within the TTL window are served from {@code verificationCache} and never hit the GitHub
   * API, so a shared token cannot be exhausted by unbounded live calls.
   */
  public AchievementVerificationResult verifySingleAchievementCached(
      String login, Achievement achievement) {
    if (login == null || login.isBlank() || achievement == null) {
      return new AchievementVerificationResult(false, 0, unavailableMessage());
    }
    AchievementVerificationCache cached = verificationCache.get(login);
    if (cached == null || cached.isExpired()) {
      verifyAchievements(login);
    }
    return verifySingleAchievement(login, achievement);
  }

  /**
   * Verifies a single achievement by querying the GitHub API. Uses the GitHub Search API to count
   * merged PRs, closed issues, etc.
   *
   * <p>Results are served from the per-user {@code verificationCache} when a fresh snapshot exists,
   * so repeated single-achievement verification (e.g. from the verify/claim endpoints) does not
   * trigger live GitHub API calls for every request. When no cached snapshot is present the
   * underlying live verification is computed and stored for the current TTL window.
   */
  public AchievementVerificationResult verifySingleAchievement(
      String login, Achievement achievement) {
    if (login == null || login.isBlank() || achievement == null) {
      return new AchievementVerificationResult(false, 0, unavailableMessage());
    }

    AchievementVerificationCache cached = verificationCache.get(login);
    if (cached != null && !cached.isExpired()) {
      for (AchievementStatus status : cached.statuses()) {
        if (status.key().equals(achievement.key())) {
          String message =
              status.unlocked()
                  ? unlockedMessage()
                  : progressMessage(status.progress(), status.threshold());
          return new AchievementVerificationResult(status.unlocked(), status.progress(), message);
        }
      }
    }

    String token = getGithubApiToken();
    try {
      int progress =
          switch (achievement.key()) {
            case "pull-shark" ->
                countSearchResults("author:" + login + " type:pr is:merged org:os-santiago", token);
            case "yolo" ->
                countSearchResults(
                    "author:" + login + " type:pr is:merged review:none org:os-santiago", token);
            case "quickdraw" -> countQuickdraw(login, token);
            case "pair-extraordinaire" -> countCoAuthoredCommits(login, token);
            case "starstruck" -> countStarredRepos(login, token);
            case "galaxy-brain" -> 0;
            case "public-sponsor" -> countSponsorships();
            case "heart-on-your-sleeve" -> countSponsorships();
            case "open-sourcerer" -> countSponsorships();
            default -> 0;
          };

      boolean verified = progress >= achievement.threshold();
      String message =
          verified ? unlockedMessage() : progressMessage(progress, achievement.threshold());
      return new AchievementVerificationResult(verified, progress, message);
    } catch (Exception e) {
      LOG.warnf(e, "Failed to verify achievement %s for %s", achievement.key(), login);
      return new AchievementVerificationResult(false, 0, unavailableMessage());
    }
  }

  private AppMessages messages() {
    return MessageBundles.get(AppMessages.class);
  }

  private String unlockedMessage() {
    return messages().achievements_verification_unlocked();
  }

  private String progressMessage(int progress, int threshold) {
    return messages().achievements_verification_progress(progress, threshold);
  }

  private String unavailableMessage() {
    return messages().achievements_verification_unavailable();
  }

  /** Counts issues/PRs closed within 5 minutes of being opened (Quickdraw achievement). */
  private int countQuickdraw(String login, String token) {
    String query = "author:" + login + " is:closed org:os-santiago";
    int count = 0;
    int page = 1;
    while (page <= 10) {
      String url =
          "https://api.github.com/search/issues?q="
              + urlEncode(query)
              + "&per_page=100&page="
              + page;
      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(REQUEST_TIMEOUT)
              .header("Accept", "application/vnd.github+json")
              .header("X-GitHub-Api-Version", "2022-11-28")
              .header("User-Agent", "homedir-achievements");
      if (token != null && !token.isBlank()) {
        builder.header("Authorization", "Bearer " + token);
      }
      try {
        HttpResponse<String> response =
            httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
          LOG.warnf("GitHub quickdraw search failed status=%d", response.statusCode());
          return count;
        }
        JsonNode json = objectMapper.readTree(response.body());
        JsonNode items = json.path("items");
        if (!items.isArray() || items.isEmpty()) {
          break;
        }
        for (JsonNode item : items) {
          String createdAt = item.path("created_at").asText(null);
          String closedAt = item.path("closed_at").asText(null);
          if (createdAt == null || closedAt == null || createdAt.isBlank() || closedAt.isBlank()) {
            continue;
          }
          try {
            Instant created = Instant.parse(createdAt);
            Instant closed = Instant.parse(closedAt);
            long seconds = Duration.between(created, closed).toSeconds();
            if (seconds >= 0 && seconds <= 300) {
              count++;
            }
          } catch (Exception ignored) {
            // skip items with unparseable dates
          }
        }
        if (items.size() < 100) {
          break;
        }
        page++;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return count;
      } catch (Exception e) {
        LOG.warnf(e, "GitHub quickdraw search failed query=%s", query);
        return count;
      }
    }
    return count;
  }

  /**
   * Counts commits in the org whose message contains a Co-authored-by trailer naming the user (Pair
   * Extraordinaire achievement). GitHub's Issues search API does not index commit message trailers,
   * so this uses the Commit search API. {@code co-authored-by:} is not a documented commit-search
   * qualifier, so it is searched as free text; the user's GitHub noreply email appears in the
   * trailer (e.g. {@code Co-authored-by: Name <login@users.noreply.github.com>}), which the
   * free-text term matches.
   */
  private int countCoAuthoredCommits(String login, String token) {
    String query = "org:os-santiago \"co-authored-by:\" " + login;
    String url = "https://api.github.com/search/commits?q=" + urlEncode(query) + "&per_page=1";
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "homedir-achievements");
    if (token != null && !token.isBlank()) {
      builder.header("Authorization", "Bearer " + token);
    }
    try {
      HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        LOG.warnf("GitHub co-authored commit search failed status=%d", response.statusCode());
        return 0;
      }
      JsonNode json = objectMapper.readTree(response.body());
      return Math.max(0, json.path("total_count").asInt(0));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return 0;
    } catch (Exception e) {
      LOG.warnf(e, "GitHub co-authored commit search failed query=%s", query);
      return 0;
    }
  }

  /** Counts GitHub Search API results for a given query. */
  int countSearchResults(String query, String token) {
    try {
      String url = "https://api.github.com/search/issues?q=" + urlEncode(query) + "&per_page=1";
      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(REQUEST_TIMEOUT)
              .header("Accept", "application/vnd.github+json")
              .header("X-GitHub-Api-Version", "2022-11-28")
              .header("User-Agent", "homedir-achievements");
      if (token != null && !token.isBlank()) {
        builder.header("Authorization", "Bearer " + token);
      }
      HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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

  /** Counts the number of starred repositories for a user. */
  int countStarredRepos(String login, String token) {
    try {
      String url = "https://api.github.com/users/" + urlEncode(login) + "/starred?per_page=1";
      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(REQUEST_TIMEOUT)
              .header("Accept", "application/vnd.github+json")
              .header("X-GitHub-Api-Version", "2022-11-28")
              .header("User-Agent", "homedir-achievements");
      if (token != null && !token.isBlank()) {
        builder.header("Authorization", "Bearer " + token);
      }
      HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        return 0;
      }
      // Parse the Link header which contains pagination info
      String linkHeader = response.headers().firstValue("link").orElse("");
      java.util.regex.Matcher matcher =
          java.util.regex.Pattern.compile("[?&]page=(\\d+)[^>]*>;\\s*rel=\"last\"")
              .matcher(linkHeader);
      if (matcher.find()) {
        return Integer.parseInt(matcher.group(1));
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
   * Counts public sponsorships. GitHub doesn't expose this via a public API, so we return 0
   * (verification not available for sponsor-based achievements).
   */
  int countSponsorships() {
    return 0;
  }

  /**
   * Awards XP to a user for a verified achievement.
   *
   * <p>The GitHub login is retrieved from the authenticated user's profile (not from direct user
   * input) and is used solely to verify the achievement via the GitHub API before awarding XP. The
   * verification step is a security control that prevents unauthorized XP awards.
   *
   * @param userId the HomeDir user ID (from authenticated session, not user input)
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
    GamificationActivity activity = activityForAchievement(achievementKey);
    if (activity == null) {
      return false;
    }
    // Verify the achievement before awarding XP. The verification result is a
    // boolean that does not carry user-controlled data.
    if (!isAchievementVerified(userId, guide)) {
      return false;
    }
    // Award XP using only the authenticated userId and static enum constants.
    // No user-controlled data is passed to the gamification service.
    return gamificationService.award(userId, activity);
  }

  /**
   * Checks whether an achievement is verified for the given user by querying the GitHub API.
   *
   * @param userId the HomeDir user ID
   * @param guide the achievement guide to verify
   * @return true if the achievement criteria are met
   */
  private boolean isAchievementVerified(String userId, AchievementGuide guide) {
    UserProfile profile = userProfiles.find(userId).orElse(null);
    if (profile == null || !profile.hasGithub()) {
      return false;
    }
    // GitHub login comes from the authenticated user's linked profile, not direct user input.
    String githubLogin = profile.getGithub().login();
    AchievementVerificationResult result =
        verifySingleAchievementCached(githubLogin, guide.achievement());
    return result.verified();
  }

  /** Maps an achievement key to a GamificationActivity. */
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
   * Builds the achievement leaderboard from all user profiles that have GitHub linked. The
   * leaderboard is sorted by unlocked achievement count (descending), then by total XP.
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
      int unlocked = (int) cached.statuses().stream().filter(AchievementStatus::unlocked).count();
      int xp =
          cached.statuses().stream()
              .filter(AchievementStatus::unlocked)
              .mapToInt(AchievementStatus::xpReward)
              .sum();
      if (unlocked > 0) {
        entries.add(
            new LeaderboardEntry(
                0,
                profile.getUserId(),
                profile.getName() != null ? profile.getName() : githubLogin,
                githubLogin,
                profile.getGithub().avatarUrl(),
                unlocked,
                xp));
      }
    }

    entries.sort(
        Comparator.comparingInt(LeaderboardEntry::unlockedCount)
            .reversed()
            .thenComparing(Comparator.comparingInt(LeaderboardEntry::totalXp).reversed())
            .thenComparing(LeaderboardEntry::githubLogin, Comparator.nullsLast(String::compareTo)));

    // Assign ranks
    List<LeaderboardEntry> ranked = new ArrayList<>();
    for (int i = 0; i < Math.min(entries.size(), LEADERBOARD_LIMIT); i++) {
      LeaderboardEntry e = entries.get(i);
      ranked.add(
          new LeaderboardEntry(
              i + 1,
              e.userId(),
              e.displayName(),
              e.githubLogin(),
              e.avatarUrl(),
              e.unlockedCount(),
              e.totalXp()));
    }

    return new Leaderboard(ranked, entries.size());
  }

  /** Returns the full catalog data for rendering the page. */
  public AchievementCatalog catalog() {
    return catalog;
  }

  /** Returns the list of GitHub highlights (badges that appear on the profile). */
  public List<Highlight> highlights() {
    AppMessages msg = messages();
    return List.of(
        new Highlight(
            "pro",
            "GitHub Pro",
            "GitHub Pro is a paid subscription that gives you access to private repositories, "
                + "advanced insights, and more. It appears as a badge on your profile.",
            "https://docs.github.com/en/get-started/learning-about-github/githubs-products",
            "/images/highlights/pro.svg",
            msg.achievement_highlight_pro_label()),
        new Highlight(
            "developer-program",
            "Developer Program Member",
            "The GitHub Developer Program is for developers who build integrations with the "
                + "GitHub API. Join at developer.github.com to get the badge.",
            "https://docs.github.com/en/developers/overview/github-developer-program",
            "/images/highlights/developer-program.svg",
            msg.achievement_highlight_developer_program_label()),
        new Highlight(
            "security-bounty",
            "Security Bug Bounty Hunter",
            "Report security vulnerabilities to GitHub's bug bounty program. Accepted reports "
                + "earn a special badge on your profile.",
            "https://bounty.github.com/",
            "/images/highlights/security-bounty.svg",
            msg.achievement_highlight_security_bounty_label()),
        new Highlight(
            "galaxy-brain-highlight",
            "Galaxy Brain (Discussions)",
            "Answer questions in GitHub Discussions. When your answer is accepted by the "
                + "question author, you earn the Galaxy Brain badge.",
            "https://docs.github.com/en/discussions",
            "/images/achievements/galaxy-brain.png",
            msg.achievement_highlight_galaxy_brain_label()));
  }

  public record Highlight(
      String key,
      String title,
      String description,
      String docUrl,
      String iconUrl,
      String profileLabel) {}

  private String getGithubApiToken() {
    return config.getOptionalValue("GH_TOKEN", String.class).orElse("").trim();
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
