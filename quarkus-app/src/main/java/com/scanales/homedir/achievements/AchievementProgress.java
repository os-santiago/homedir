package com.scanales.homedir.achievements;

/** Per-user progress toward a single achievement, produced by {@link AchievementService}. */
public record AchievementProgress(
    String achievementKey, int current, int threshold, Status status) {

  public enum Status {
    LOCKED,
    IN_PROGRESS,
    COMPLETED
  }

  /** Factory for the "not verified yet" state (anonymous or pre-verification). */
  public static AchievementProgress locked(Achievement achievement) {
    return new AchievementProgress(achievement.key(), 0, achievement.threshold(), Status.LOCKED);
  }

  /** Factory from a raw count returned by the GitHub API. */
  public static AchievementProgress fromCount(Achievement achievement, int count) {
    if (count >= achievement.threshold()) {
      return new AchievementProgress(
          achievement.key(), count, achievement.threshold(), Status.COMPLETED);
    }
    if (count > 0) {
      return new AchievementProgress(
          achievement.key(), count, achievement.threshold(), Status.IN_PROGRESS);
    }
    return locked(achievement);
  }

  /** Progress as a percentage string for the template (0–100). */
  public int percent() {
    if (threshold <= 0) {
      return 0;
    }
    return Math.min(100, (current * 100) / threshold);
  }
}
