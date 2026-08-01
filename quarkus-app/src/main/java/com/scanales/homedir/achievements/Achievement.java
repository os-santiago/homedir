package com.scanales.homedir.achievements;

/**
 * A GitHub achievement/highlight that HomeDir helps community members unlock.
 *
 * <p>Each achievement has a {@link VerificationType} that determines how progress is measured
 * against the GitHub API, a {@code threshold} (the count needed to complete it), and a {@code
 * docUrl} pointing to the reference resource describing how to earn it.
 */
public record Achievement(
    String key,
    String title,
    String description,
    String descriptionEs,
    String category,
    String docUrl,
    int threshold,
    VerificationType verification) {

  /** How progress toward this achievement is measured via the GitHub API. */
  public enum VerificationType {
    /** Count merged PRs by the user across os-santiago repos (Pull Shark). */
    MERGED_PRS,
    /** Count co-authored merged PRs (Pair Extraordinaire). */
    COAUTHORED_PRS,
    /** Count merged PRs without review (YOLO). */
    MERGED_PRS_NO_REVIEW,
    /** Check if any org repo has reached the star threshold (Starstruck). */
    REPO_STARS,
    /** Cannot be verified via the public GitHub API (manual / future). */
    MANUAL_ONLY
  }

  /** Convenience factory. */
  public static Achievement of(
      String key,
      String title,
      String description,
      String descriptionEs,
      String category,
      String docUrl,
      int threshold,
      VerificationType verification) {
    return new Achievement(
        key, title, description, descriptionEs, category, docUrl, threshold, verification);
  }
}
