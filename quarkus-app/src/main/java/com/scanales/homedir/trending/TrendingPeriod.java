package com.scanales.homedir.trending;

public enum TrendingPeriod {
  DAILY("daily"),
  WEEKLY("weekly"),
  MONTHLY("monthly");

  private final String path;

  TrendingPeriod(String path) {
    this.path = path;
  }

  public String toGithubPath() {
    return path;
  }

  public static TrendingPeriod fromString(String value) {
    if (value == null || value.isBlank()) {
      return DAILY;
    }
    String normalized = value.trim().toLowerCase();
    return switch (normalized) {
      case "weekly" -> WEEKLY;
      case "monthly" -> MONTHLY;
      default -> DAILY;
    };
  }
}
