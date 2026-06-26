package com.scanales.homedir.reputation.bounty;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for an issue impact label and its associated points.
 */
public record IssueImpactLabel(
    @JsonProperty("label_name") String labelName, @JsonProperty("points") long points) {

  public IssueImpactLabel {
    if (labelName == null || labelName.isBlank()) {
      throw new IllegalArgumentException("Label name cannot be null or blank");
    }
    if (points < 0) {
      throw new IllegalArgumentException("Points cannot be negative");
    }
  }
}
