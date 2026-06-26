package com.scanales.homedir.reputation.bounty;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Locale;

/**
 * Represents a user's accumulated Bounty Hunter score and level.
 * Tracks points from issue creation and resolution activities.
 */
public record BountyHunterScore(
    @JsonProperty("user_id") String userId,
    @JsonProperty("total_points") long totalPoints,
    @JsonProperty("issue_creation_points") long issueCreationPoints,
    @JsonProperty("issue_resolution_points") long issueResolutionPoints,
    @JsonProperty("current_level") BountyHunterLevel currentLevel,
    @JsonProperty("issues_created_count") int issuesCreatedCount,
    @JsonProperty("issues_resolved_count") int issuesResolvedCount,
    @JsonProperty("updated_at") Instant updatedAt) {

  public BountyHunterScore {
    userId = normalizeUserId(userId);
    totalPoints = Math.max(0L, totalPoints);
    issueCreationPoints = Math.max(0L, issueCreationPoints);
    issueResolutionPoints = Math.max(0L, issueResolutionPoints);
    currentLevel = currentLevel == null ? BountyHunterLevel.NONE : currentLevel;
    issuesCreatedCount = Math.max(0, issuesCreatedCount);
    issuesResolvedCount = Math.max(0, issuesResolvedCount);
    updatedAt = updatedAt == null ? Instant.now() : updatedAt;
  }

  private static String normalizeUserId(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("userId cannot be null or blank");
    }
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  public BountyHunterScore withAddedIssueCreationPoints(long points, String issueNumber) {
    long newCreationPoints = this.issueCreationPoints + points;
    long newTotalPoints = this.totalPoints + points;
    BountyHunterLevel newLevel = BountyHunterLevel.fromPoints(newTotalPoints);
    return new BountyHunterScore(
        this.userId,
        newTotalPoints,
        newCreationPoints,
        this.issueResolutionPoints,
        newLevel,
        this.issuesCreatedCount + 1,
        this.issuesResolvedCount,
        Instant.now());
  }

  public BountyHunterScore withAddedIssueResolutionPoints(long points, String issueNumber) {
    long newResolutionPoints = this.issueResolutionPoints + points;
    long newTotalPoints = this.totalPoints + points;
    BountyHunterLevel newLevel = BountyHunterLevel.fromPoints(newTotalPoints);
    return new BountyHunterScore(
        this.userId,
        newTotalPoints,
        this.issueCreationPoints,
        newResolutionPoints,
        newLevel,
        this.issuesCreatedCount,
        this.issuesResolvedCount + 1,
        Instant.now());
  }
}
