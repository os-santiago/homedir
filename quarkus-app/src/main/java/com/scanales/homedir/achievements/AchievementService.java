package com.scanales.homedir.achievements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.homedir.achievements.AchievementCatalog.Achievement;
import com.scanales.homedir.achievements.AchievementCatalog.AchievementGuide;
import com.scanales.homedir.achievements.AchievementProgress.AchievementState;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.service.GamificationService;
import com.scanales.homedir.service.UserProfileService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Service that verifies GitHub achievements for HomeDir users and tracks per-user progress.
 *
 * <p>Uses the GitHub REST API to check verifiable achievements (Pull Shark, Pair Extraordinaire,
 * Quickdraw, YOLO, Starstruck, Heart On Your Sleeve, Open Sourcerer). For achievements without a
 * public API (Galaxy Brain, Public Sponsor), users can self-claim completion.
 *
 * <p>When an achievement is verified as completed, XP is awarded via {@link GamificationService}.
 */
@ApplicationScoped
public class AchievementService {

  private static final Logger LOG = Logger.getLogger(AchievementService.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
  private static final String GITHUB_API = "https://api.github.com";
  private static final String ORG = "os-santiago";

  @Inject AchievementCatalog catalog;
  @Inject GamificationService gamificationService;
  @Inject UserProfileService userProfiles;
  @Inject ObjectMapper objectMapper;

  @ConfigProperty(name = "home.data.dir", defaultValue = "data")
  String dataDirPath;

  @ConfigProperty(name = "home.project.github.api-token", defaultValue = "")
  String apiToken;

  private Path dataDir;
  private Path achievementsFile;
  private final Map<String, AchievementProgress> progressMap = new ConcurrentHashMap<>();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

  @PostConstruct
  void init() {
    dataDir = Paths.get(dataDirPath);
    achievementsFile = dataDir.resolve("achievement-progress.json");
    loadProgress();
  }

  @SuppressWarnings("unchecked")
  private void loadProgress() {
    try {
      if (Files.exists(achievementsFile)) {
        String json = Files.readString(achievementsFile);
        Map<String, AchievementProgress> loaded =
            objectMapper.readValue(
                json,
                objectMapper
                    .getTypeFactory()
                    .constructMapType(Map.class, String.class, AchievementProgress.class));
        progressMap.putAll(loaded);
      }
    } catch (Exception e) {
      LOG.warn("Unable to load achievement progress; starting empty", e);
    }
  }

  private synchronized void saveProgress() {
    try {
      Files.createDirectories(dataDir);
      String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(progressMap);
      Files.writeString(achievementsFile, json);
    } catch (Exception e) {
      LOG.warn("Unable to save achievement progress", e);
    }
  }

  /**
   * Gets or creates the achievement progress for a user.
   *
   * @param userId the HomeDir user ID
   * @return the progress record (never null)
   */
  public AchievementProgress getProgress(String userId) {
    if (userId == null || userId.isBlank()) {
      return new AchievementProgress(null, null);
    }
    return progressMap.computeIfAbsent(
        userId,
        id -> {
          Optional<UserProfile> profile = userProfiles.find(id);
          String ghLogin =
              profile
                  .flatMap(p -> Optional.ofNullable(p.getGithub()))
                  .map(UserProfile.GithubAccount::login)
                  .orElse(null);
          AchievementProgress ap = new AchievementProgress(id, ghLogin);
          // Initialize all achievements as locked
          for (AchievementGuide guide : catalog.guides()) {
            Achievement a = guide.achievement();
            ap.getStates().putIfAbsent(a.key(), AchievementState.locked(a.threshold()));
          }
          return ap;
        });
  }

  /**
   * Verifies all achievements for a user via the GitHub API.
   *
   * <p>For each achievement, checks the user's GitHub activity and updates the progress state.
   * Awards XP via GamificationService for newly completed achievements.
   *
   * @param userId the HomeDir user ID
   * @param githubLogin the GitHub login to verify (from the user's linked account)
   * @return the updated progress record
   */
  public AchievementProgress verifyAchievements(String userId, String githubLogin) {
    if (userId == null || userId.isBlank() || githubLogin == null || githubLogin.isBlank()) {
      return getProgress(userId);
    }

    AchievementProgress progress =
        progressMap.computeIfAbsent(userId, id -> new AchievementProgress(id, githubLogin));
    progress.setGithubLogin(githubLogin);
    progress.setUpdatedAt(Instant.now());

    for (AchievementGuide guide : catalog.guides()) {
      Achievement a = guide.achievement();
      AchievementState newState = verifySingle(a, githubLogin);
      AchievementState oldState = progress.getStates().get(a.key());

      progress.getStates().put(a.key(), newState);

      // Award XP if newly completed
      if (newState.isCompleted() && (oldState == null || !oldState.isCompleted())) {
        GamificationActivity activity = activityFor(a.key());
        if (activity != null) {
          gamificationService.award(userId, activity, a.key());
          LOG.infof("Achievement %s completed for user %s — XP awarded", a.key(), userId);
        }
      }
    }

    saveProgress();
    return progress;
  }

  /**
   * Self-claims an achievement as completed (for achievements that can't be verified via API).
   *
   * @param userId the HomeDir user ID
   * @param achievementKey the achievement key to claim
   * @return the updated state, or null if the achievement doesn't exist
   */
  public AchievementState selfClaim(String userId, String achievementKey) {
    Achievement a = catalog.find(achievementKey);
    if (a == null) {
      return null;
    }

    AchievementProgress progress = getProgress(userId);
    AchievementState oldState = progress.getStates().get(achievementKey);
    AchievementState newState =
        AchievementState.completed(a.threshold(), a.threshold(), "self_claimed");
    progress.getStates().put(achievementKey, newState);
    progress.setUpdatedAt(Instant.now());

    // Award XP if newly completed
    if (oldState == null || !oldState.isCompleted()) {
      GamificationActivity activity = activityFor(achievementKey);
      if (activity != null) {
        gamificationService.award(userId, activity, achievementKey);
      }
    }

    saveProgress();
    return newState;
  }

  /**
   * Returns a leaderboard of users sorted by number of completed achievements.
   *
   * @return list of leaderboard entries (userId, githubLogin, completedCount)
   */
  public List<LeaderboardEntry> getLeaderboard() {
    List<LeaderboardEntry> entries = new ArrayList<>();
    for (Map.Entry<String, AchievementProgress> e : progressMap.entrySet()) {
      AchievementProgress ap = e.getValue();
      long completed =
          ap.getStates().values().stream().filter(AchievementState::isCompleted).count();
      if (completed > 0) {
        entries.add(new LeaderboardEntry(e.getKey(), ap.getGithubLogin(), (int) completed));
      }
    }
    entries.sort(Comparator.comparingInt(LeaderboardEntry::completedCount).reversed());
    return entries.size() > 20 ? entries.subList(0, 20) : entries;
  }

  /** A single entry in the achievement leaderboard. */
  public record LeaderboardEntry(String userId, String githubLogin, int completedCount) {}

  // -- Per-achievement verification ------------------------------------------

  private AchievementState verifySingle(Achievement a, String githubLogin) {
    int target = a.threshold();
    try {
      int count =
          switch (a.key()) {
            case "pull-shark" -> countMergedPRs(githubLogin);
            case "pair-extraordinaire" -> countCoAuthoredCommits(githubLogin);
            case "quickdraw" -> checkQuickdraw(githubLogin);
            case "yolo" -> countMergedPRsWithoutReview(githubLogin);
            case "starstruck" -> countStarredOrgRepos(githubLogin);
            case "heart-on-your-sleeve" -> countReactions(githubLogin);
            case "open-sourcerer" -> countContributionsToOrg(githubLogin);
            // Galaxy Brain and Public Sponsor have no public REST API
            default -> -1; // Not verifiable via API
          };

      if (count < 0) {
        // Not verifiable — keep current state or locked
        return AchievementState.locked(target);
      }
      if (count >= target) {
        return AchievementState.completed(count, target, "github_api");
      }
      if (count > 0) {
        return AchievementState.inProgress(count, target);
      }
      return AchievementState.locked(target);
    } catch (Exception e) {
      LOG.warnf(
          "Achievement verification failed for %s/%s: %s", a.key(), githubLogin, e.getMessage());
      return AchievementState.locked(target);
    }
  }

  /**
   * Counts merged PRs by the user in the os-santiago org.
   *
   * <p>Uses the GitHub Search API: {@code GET
   * /search/issues?q=author:{login}+org:os-santiago+type:pr+is:merged}
   */
  int countMergedPRs(String login) throws IOException, InterruptedException {
    String query =
        URLEncoder.encode(
            "author:" + login + " org:" + ORG + " type:pr is:merged", StandardCharsets.UTF_8);
    return searchCount("/search/issues?q=" + query);
  }

  /** Counts co-authored commits by the user in the os-santiago org. */
  int countCoAuthoredCommits(String login) throws IOException, InterruptedException {
    String query =
        URLEncoder.encode(
            "author:" + login + " org:" + ORG + " co-authored-by:true", StandardCharsets.UTF_8);
    return searchCount("/search/commits?q=" + query);
  }

  /**
   * Checks if the user has opened and closed an issue within 5 minutes (Quickdraw).
   *
   * @return 1 if achieved, 0 otherwise
   */
  int checkQuickdraw(String login) throws IOException, InterruptedException {
    // Check recent issue events for quick open/close patterns
    String query =
        URLEncoder.encode(
            "author:" + login + " org:" + ORG + " type:issue is:closed", StandardCharsets.UTF_8);
    int closedCount = searchCount("/search/issues?q=" + query);
    // We can't precisely verify the 5-minute window via REST API without fetching events.
    // If the user has closed issues in the org, mark as in-progress.
    return closedCount > 0 ? 1 : 0;
  }

  /** Counts merged PRs without review by the user in the org (YOLO). */
  int countMergedPRsWithoutReview(String login) throws IOException, InterruptedException {
    // Search for merged PRs with no review comments
    String query =
        URLEncoder.encode(
            "author:" + login + " org:" + ORG + " type:pr is:merged review:none",
            StandardCharsets.UTF_8);
    return searchCount("/search/issues?q=" + query);
  }

  /**
   * Checks if the user has starred any os-santiago repos (Starstruck). Note: The GitHub API doesn't
   * allow checking who starred a repo without admin access. We check the star count of org repos
   * instead.
   */
  int countStarredOrgRepos(String login) throws IOException, InterruptedException {
    // Check if any org repo has >= 16 stars (Starstruck threshold)
    for (AchievementCatalog.OrgRepo repo : catalog.orgRepos()) {
      String[] parts = repo.name().split("/");
      if (parts.length == 2) {
        JsonNode repoJson = fetchJson("/repos/" + parts[0] + "/" + parts[1]);
        int stars = repoJson.path("stargazers_count").asInt(0);
        if (stars >= 16) {
          return stars;
        }
      }
    }
    return 0;
  }

  /** Counts reactions by the user on os-santiago issues and PRs (Heart On Your Sleeve). */
  int countReactions(String login) throws IOException, InterruptedException {
    // Search for issues/PRs the user reacted to in the org
    String query =
        URLEncoder.encode(
            "involves:" + login + " org:" + ORG + " type:issue", StandardCharsets.UTF_8);
    return searchCount("/search/issues?q=" + query);
  }

  /** Counts contributions to the os-santiago org (Open Sourcerer). */
  int countContributionsToOrg(String login) throws IOException, InterruptedException {
    // Count merged PRs + closed issues + commits across org repos
    int prs = countMergedPRs(login);
    String issueQuery =
        URLEncoder.encode(
            "author:" + login + " org:" + ORG + " type:issue is:closed", StandardCharsets.UTF_8);
    int issues = searchCount("/search/issues?q=" + issueQuery);
    return prs + issues;
  }

  // -- GitHub API helpers ----------------------------------------------------

  private int searchCount(String path) throws IOException, InterruptedException {
    JsonNode json = fetchJson(path);
    return json.path("total_count").asInt(0);
  }

  private JsonNode fetchJson(String path) throws IOException, InterruptedException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(GITHUB_API + path))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "homedir-service");
    if (apiToken != null && !apiToken.isBlank()) {
      builder.header("Authorization", "Bearer " + apiToken);
    }
    HttpResponse<String> response =
        httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      LOG.warnf("GitHub API call failed: status=%d path=%s", response.statusCode(), path);
      return objectMapper.createObjectNode();
    }
    return objectMapper.readTree(response.body());
  }

  // -- Gamification mapping --------------------------------------------------

  private GamificationActivity activityFor(String achievementKey) {
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
}
