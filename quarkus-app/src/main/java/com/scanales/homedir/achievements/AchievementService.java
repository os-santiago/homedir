package com.scanales.homedir.achievements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.service.GamificationService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

/**
 * Verifies per-user progress toward GitHub achievements using the GitHub REST/Search API and awards
 * XP via {@link GamificationService} when an achievement is completed.
 *
 * <p>Results are cached per GitHub login with a 1-hour TTL. The service uses the server-side {@code
 * GH_TOKEN} for API authentication, following the same pattern as {@code GithubService}.
 */
@ApplicationScoped
public class AchievementService {

  private static final Logger LOG = Logger.getLogger(AchievementService.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(1);
  private static final String ORG = "os-santiago";
  /**
   * GitHub usernames may contain only alphanumeric characters and hyphens, max 39 chars, and cannot
   * start or end with a hyphen. This pattern is used to sanitize login values before they are
   * interpolated into GitHub Search API queries to prevent query injection.
   */
  private static final Pattern GITHUB_LOGIN_PATTERN = Pattern.compile("^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$");

  @Inject AchievementCatalog catalog;
  @Inject GamificationService gamificationService;
  @Inject ObjectMapper objectMapper;
  @Inject Config config;

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

  private final ConcurrentHashMap<String, CachedVerification> cache = new ConcurrentHashMap<>();

  /** Result of verifying a user's achievements. */
  public record VerificationResult(
      String githubLogin,
      List<AchievementProgress> progress,
      List<String> newlyCompleted,
      Instant verifiedAt) {}

  /**
   * Returns cached progress for the given GitHub login, or {@code null} if no cached result exists
   * or the cache has expired.
   */
  public List<AchievementProgress> getCachedProgress(String githubLogin) {
    if (githubLogin == null || githubLogin.isBlank()) {
      return null;
    }
    CachedVerification cached = cache.get(githubLogin.toLowerCase());
    if (cached == null || cached.isExpired()) {
      return null;
    }
    return cached.progress;
  }

  /**
   * Verifies the user's progress toward all achievements by calling the GitHub API. Awards XP via
   * {@link GamificationService} for any achievement that transitions to COMPLETED.
   *
   * @param githubLogin the GitHub login of the user to verify
   * @param userId the HomeDir user ID (for gamification awards); may be {@code null} for anonymous
   * @return the verification result with per-achievement progress and newly-completed keys
   */
  public VerificationResult verify(String githubLogin, String userId) {
    if (githubLogin == null || githubLogin.isBlank()) {
      return new VerificationResult(githubLogin, allLocked(), List.of(), Instant.now());
    }
    if (!GITHUB_LOGIN_PATTERN.matcher(githubLogin).matches()) {
      LOG.warnf("achievement_verify_rejected_invalid_login login=%s", githubLogin);
      return new VerificationResult(githubLogin, allLocked(), List.of(), Instant.now());
    }

    String token = getGithubApiToken();
    List<AchievementProgress> progressList = new ArrayList<>();
    List<String> newlyCompleted = new ArrayList<>();

    // Track which were previously completed (from cache) to detect transitions.
    List<AchievementProgress> previous = getCachedProgress(githubLogin);
    Map<String, AchievementProgress.Status> prevStatuses = new HashMap<>();
    if (previous != null) {
      for (AchievementProgress p : previous) {
        prevStatuses.put(p.achievementKey(), p.status());
      }
    }

    for (AchievementCatalog.AchievementGuide guide : catalog.guides()) {
      Achievement achievement = guide.achievement();
      AchievementProgress progress;
      try {
        int count = verifyAchievement(achievement, githubLogin, token);
        progress = AchievementProgress.fromCount(achievement, count);
      } catch (Exception e) {
        LOG.warnf(e, "achievement_verify_failed key=%s login=%s", achievement.key(), githubLogin);
        progress = AchievementProgress.locked(achievement);
      }
      progressList.add(progress);

      // Award XP if the achievement just transitioned to COMPLETED.
      if (progress.status() == AchievementProgress.Status.COMPLETED) {
        AchievementProgress.Status prev = prevStatuses.get(achievement.key());
        if (prev != AchievementProgress.Status.COMPLETED) {
          newlyCompleted.add(achievement.key());
          if (userId != null && !userId.isBlank()) {
            awardAchievementXp(userId, achievement);
          }
        }
      }
    }

    cache.put(githubLogin.toLowerCase(), new CachedVerification(progressList, Instant.now()));
    return new VerificationResult(githubLogin, progressList, newlyCompleted, Instant.now());
  }

  /** Clears the cached verification for a given GitHub login. */
  public void clearCache(String githubLogin) {
    if (githubLogin != null && !githubLogin.isBlank()) {
      cache.remove(githubLogin.toLowerCase());
    }
  }

  // -- GitHub API calls -----------------------------------------------------

  private int verifyAchievement(Achievement achievement, String login, String token)
      throws IOException, InterruptedException {
    return switch (achievement.verification()) {
      case MERGED_PRS ->
          countSearchResults("is:pr is:merged author:" + login + " org:" + ORG, token);
      case COAUTHORED_PRS ->
          countSearchResults("is:pr is:merged co-authored-by:" + login + " org:" + ORG, token);
      case MERGED_PRS_NO_REVIEW ->
          countSearchResults(
              "is:pr is:merged author:" + login + " org:" + ORG + " review:none", token);
      case REPO_STARS -> maxRepoStars(achievement.threshold(), token);
      case MANUAL_ONLY -> 0;
    };
  }

  private int countSearchResults(String query, String token)
      throws IOException, InterruptedException {
    String url =
        "https://api.github.com/search/issues?q="
            + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&per_page=1";
    JsonNode json = apiGetJson(url, token);
    return Math.max(0, json.path("total_count").asInt(0));
  }

  private int maxRepoStars(int threshold, String token) throws IOException, InterruptedException {
    int maxStars = 0;
    for (AchievementCatalog.OrgRepo repo : catalog.orgRepos()) {
      // repo.name is "os-santiago/homedir" → owner=os-santiago, repo=homedir
      String[] parts = repo.name().split("/", 2);
      if (parts.length != 2) {
        continue;
      }
      String url = "https://api.github.com/repos/" + parts[0] + "/" + parts[1];
      try {
        JsonNode json = apiGetJson(url, token);
        int stars = json.path("stargazers_count").asInt(0);
        if (stars > maxStars) {
          maxStars = stars;
        }
        if (maxStars >= threshold) {
          break;
        }
      } catch (IOException e) {
        LOG.warnf("achievement_repo_stars_fetch_failed repo=%s", repo.name());
      }
    }
    return maxStars;
  }

  private JsonNode apiGetJson(String url, String token) throws IOException, InterruptedException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "homedir-service");
    if (token != null && !token.isBlank()) {
      builder.header("Authorization", "Bearer " + token);
    }
    HttpResponse<String> response =
        httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      throw new IOException("GitHub API error: status=" + response.statusCode());
    }
    return objectMapper.readTree(response.body());
  }

  // -- Gamification ---------------------------------------------------------

  private void awardAchievementXp(String userId, Achievement achievement) {
    try {
      GamificationActivity activity = gamificationActivityFor(achievement.key());
      if (activity != null) {
        gamificationService.award(userId, activity, achievement.key());
        LOG.infof(
            "achievement_xp_awarded user=%s key=%s xp=%d",
            userId, achievement.key(), activity.xp());
      }
    } catch (Exception e) {
      LOG.warnf(e, "achievement_xp_award_failed user=%s key=%s", userId, achievement.key());
    }
  }

  private GamificationActivity gamificationActivityFor(String achievementKey) {
    return switch (achievementKey) {
      case "pull-shark" -> GamificationActivity.ACHIEVEMENT_PULL_SHARK;
      case "pair-extraordinaire" -> GamificationActivity.ACHIEVEMENT_PAIR_EXTRAORDINAIRE;
      case "yolo" -> GamificationActivity.ACHIEVEMENT_YOLO;
      case "starstruck" -> GamificationActivity.ACHIEVEMENT_STARSTRUCK;
      case "quickdraw" -> GamificationActivity.ACHIEVEMENT_QUICKDRAW;
      case "galaxy-brain" -> GamificationActivity.ACHIEVEMENT_GALAXY_BRAIN;
      case "public-sponsor" -> GamificationActivity.ACHIEVEMENT_PUBLIC_SPONSOR;
      case "heart-on-your-sleeve" -> GamificationActivity.ACHIEVEMENT_HEART_ON_SLEEVE;
      case "open-sourcerer" -> GamificationActivity.ACHIEVEMENT_OPEN_SOURCERER;
      default -> null;
    };
  }

  // -- Helpers --------------------------------------------------------------

  private List<AchievementProgress> allLocked() {
    return catalog.guides().stream().map(g -> AchievementProgress.locked(g.achievement())).toList();
  }

  private String getGithubApiToken() {
    return config.getOptionalValue("GH_TOKEN", String.class).orElse("").trim();
  }

  private record CachedVerification(List<AchievementProgress> progress, Instant cachedAt) {
    boolean isExpired() {
      return cachedAt == null || Instant.now().isAfter(cachedAt.plus(DEFAULT_CACHE_TTL));
    }
  }
}
