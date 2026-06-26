package com.scanales.homedir.reputation.bounty;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Configuration for eligible issue labels and their point values. */
public record IssueImpactLabel(
    @JsonProperty("label_name") String labelName,
    @JsonProperty("points") long points,
    @JsonProperty("enabled") boolean enabled,
    @JsonProperty("description") String description) {

  public IssueImpactLabel(String labelName, long points) {
    this(labelName, points, true, labelName);
  }

  public static IssueImpactLabel bugLow() {
    return new IssueImpactLabel("bug-impact-low", 5L, true, "Low impact bug fix");
  }

  public static IssueImpactLabel bugMedium() {
    return new IssueImpactLabel("bug-impact-medium", 15L, true, "Medium impact bug fix");
  }

  public static IssueImpactLabel bugHigh() {
    return new IssueImpactLabel("bug-impact-high", 30L, true, "High impact bug fix");
  }

  public static IssueImpactLabel featureRequest() {
    return new IssueImpactLabel("feature-request", 20L, true, "Feature request or enhancement");
  }

  public static IssueImpactLabel documentationImprovement() {
    return new IssueImpactLabel(
        "documentation-improvement", 10L, true, "Documentation improvement");
  }

  public static IssueImpactLabel platformMaintenance() {
    return new IssueImpactLabel("platform-maintenance", 15L, true, "Platform maintenance task");
  }
}
