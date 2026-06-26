package com.scanales.homedir.reputation.bounty;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Locale;

/**
 * Represents the accumulated Bounty Hunter score for a user.
 * Tracks issue creation and resolution reputation points.
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
    userId = normalizeUser(userId);
    totalPoints = Math.max(0L, totalPoints);
    issueCreationPoints = Math.max(0L, issueCreationPoints);
    issueResolutionPoints = Math.max(0L, issueResolutionPoints);
    currentLevel = BountyHunterLevel.fromPoints(totalPoints);
    issuesCreatedCount = Math.max(0, issuesCreatedCount);
    issuesResolvedCount = Math.max(0, issuesResolvedCount);
    updatedAt = updatedAt == null ? Instant.now() : updatedAt;
  }

  private static String normalizeUser(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  public BountyHunterScore withAddedIssueCreationPoints(long points, String issueNumber) {
    long newIssuePoints = issueCreationPoints + points;
    long newTotal = totalPoints + points;
    int newCount = issuesCreatedCount + 1;
    BountyHunterLevel newLevel = BountyHunterLevel.fromPoints(newTotal);
    return new BountyHunterScore(
        userId,
        newTotal,
        newIssuePoints,
        issueResolutionPoints,
        newLevel,
        newCount,
        issuesResolvedCount,
        Instant.now());
  }

  public BountyHunterScore withAddedIssueResolutionPoints(long points, String issueNumber) {
    long newResolutionPoints = issueResolutionPoints + points;
    long newTotal = totalPoints + points;
    int newCount = issuesResolvedCount + 1;
    BountyHunterLevel newLevel = BountyHunterLevel.fromPoints(newTotal);
    return new BountyHunterScore(
        userId,
        newTotal,
        issueCreationPoints,
        newResolutionPoints,
        newLevel,
        issuesCreatedCount,
        newCount,
        Instant.now());
  }
}
