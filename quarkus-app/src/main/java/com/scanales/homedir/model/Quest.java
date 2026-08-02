package com.scanales.homedir.model;

public record Quest(
    String id,
    String title,
    String description,
    int xpReward,
    String difficulty, // E, D, C, B, A, S, SS
    String status, // OPEN, IN_PROGRESS, CLOSED
    String url,
    java.util.List<String> assignees,
    java.util.List<String> labels,
    boolean repeatable,
    String titleEn,
    String descriptionEn) {

  // Convenience constructor for backward compatibility (defaults
  // repeatable=false and no English overrides)
  public Quest(
      String id,
      String title,
      String description,
      int xpReward,
      String difficulty,
      String status,
      String url,
      java.util.List<String> assignees,
      java.util.List<String> labels) {
    this(
        id,
        title,
        description,
        xpReward,
        difficulty,
        status,
        url,
        assignees,
        labels,
        false,
        null,
        null);
  }
}
