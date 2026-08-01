package com.scanales.homedir.achievements;

/**
 * A GitHub achievement/highlight that HomeDir helps community members unlock.
 *
 * <p>Phase 1 (issue #1323, parent #1043): the catalog is static and the state is always {@link
 * Status#LOCKED} because real GitHub API verification is a later phase. The {@code docUrl} points
 * to the official/reference resource describing how to earn the achievement.
 */
public record Achievement(
    String key,
    String title,
    String description,
    String descriptionEs,
    String category,
    String docUrl,
    Status status) {

  public enum Status {
    LOCKED,
    IN_PROGRESS,
    COMPLETED
  }

  /** Convenience factory for the default Phase 1 state (LOCKED). */
  public static Achievement locked(
      String key,
      String title,
      String description,
      String descriptionEs,
      String category,
      String docUrl) {
    return new Achievement(key, title, description, descriptionEs, category, docUrl, Status.LOCKED);
  }
}
