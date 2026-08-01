package com.scanales.homedir.achievements;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user achievement progress tracking.
 *
 * <p>Stores the verification state for each achievement key, along with the timestamp of the last
 * verification attempt and the GitHub login used for verification.
 */
public class AchievementProgress {

  private String userId;
  private String githubLogin;
  private final Map<String, AchievementState> states = new ConcurrentHashMap<>();
  private Instant updatedAt;

  public AchievementProgress() {
    this(null, null);
  }

  public AchievementProgress(String userId, String githubLogin) {
    this.userId = userId;
    this.githubLogin = githubLogin;
    this.updatedAt = Instant.now();
  }

  @JsonCreator
  public AchievementProgress(
      @JsonProperty("userId") String userId,
      @JsonProperty("githubLogin") String githubLogin,
      @JsonProperty("states") Map<String, AchievementState> states,
      @JsonProperty("updatedAt") Instant updatedAt) {
    this.userId = userId;
    this.githubLogin = githubLogin;
    if (states != null) {
      this.states.putAll(states);
    }
    this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getGithubLogin() {
    return githubLogin;
  }

  public void setGithubLogin(String githubLogin) {
    this.githubLogin = githubLogin;
  }

  public Map<String, AchievementState> getStates() {
    return states;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  /**
   * The verification state of a single achievement.
   *
   * @param status "locked", "in_progress", or "completed"
   * @param verifiedAt timestamp when the achievement was verified (null if not completed)
   * @param verifiedVia "github_api" or "self_claimed"
   * @param progressCount number of qualifying actions (e.g. merged PRs for Pull Shark)
   * @param progressTarget threshold to unlock the achievement
   */
  public record AchievementState(
      String status,
      Instant verifiedAt,
      String verifiedVia,
      int progressCount,
      int progressTarget) {

    @JsonCreator
    public AchievementState(
        @JsonProperty("status") String status,
        @JsonProperty("verifiedAt") Instant verifiedAt,
        @JsonProperty("verifiedVia") String verifiedVia,
        @JsonProperty("progressCount") int progressCount,
        @JsonProperty("progressTarget") int progressTarget) {
      this.status = status;
      this.verifiedAt = verifiedAt;
      this.verifiedVia = verifiedVia;
      this.progressCount = progressCount;
      this.progressTarget = progressTarget;
    }

    public static AchievementState locked(int target) {
      return new AchievementState("locked", null, null, 0, target);
    }

    public static AchievementState inProgress(int count, int target) {
      return new AchievementState("in_progress", null, null, count, target);
    }

    public static AchievementState completed(int count, int target, String via) {
      return new AchievementState("completed", Instant.now(), via, count, target);
    }

    public boolean isCompleted() {
      return "completed".equals(status);
    }
  }
}
