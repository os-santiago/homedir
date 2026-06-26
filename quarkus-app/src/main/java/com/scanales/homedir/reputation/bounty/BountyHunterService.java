package com.scanales.homedir.reputation.bounty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for the Bounty Hunter program.
 * Handles scoring, level progression, and event tracking.
 */
@ApplicationScoped
public class BountyHunterService {

  @Inject BountyHunterRepository repository;
  @Inject BountyHunterConfigService configService;

  /**
   * Award points for issue creation when validated by an administrator.
   */
  public BountyHunterScore awardIssueCreationPoints(
      String userId, String issueNumber, String label, String validatedByUserId) {

    long points = configService.getPointsForLabel(label);
    if (points <= 0) {
      throw new IllegalArgumentException("Label '" + label + "' is not eligible for points");
    }

    if (!configService.isAdminAccount(validatedByUserId)) {
      throw new IllegalArgumentException(
          "User '" + validatedByUserId + "' is not authorized to validate issues");
    }

    BountyHunterScore current =
        repository
            .findScoreByUserId(userId)
            .orElse(
                new BountyHunterScore(
                    userId, 0L, 0L, 0L, BountyHunterLevel.NONE, 0, 0, Instant.now()));

    BountyHunterScore updated = current.withAddedIssueCreationPoints(points, issueNumber);
    repository.saveScore(updated);

    BountyHunterEvent event =
        new BountyHunterEvent(
            generateEventId(),
            userId,
            BountyHunterEventType.ISSUE_LABEL_APPROVED,
            issueNumber,
            null,
            points,
            label,
            validatedByUserId,
            Instant.now());
    repository.appendEvent(event);

    return updated;
  }

  /**
   * Award points for issue resolution via approved PR.
   */
  public BountyHunterScore awardIssueResolutionPoints(
      String userId, String issueNumber, String prNumber, String label) {

    long points = configService.getPointsForLabel(label);
    if (points <= 0) {
      throw new IllegalArgumentException("Label '" + label + "' is not eligible for points");
    }

    BountyHunterScore current =
        repository
            .findScoreByUserId(userId)
            .orElse(
                new BountyHunterScore(
                    userId, 0L, 0L, 0L, BountyHunterLevel.NONE, 0, 0, Instant.now()));

    BountyHunterScore updated = current.withAddedIssueResolutionPoints(points, issueNumber);
    repository.saveScore(updated);

    BountyHunterEvent event =
        new BountyHunterEvent(
            generateEventId(),
            userId,
            BountyHunterEventType.ISSUE_RESOLVED_BY_PR,
            issueNumber,
            prNumber,
            points,
            label,
            null,
            Instant.now());
    repository.appendEvent(event);

    return updated;
  }

  public Optional<BountyHunterScore> getScoreForUser(String userId) {
    return repository.findScoreByUserId(userId);
  }

  public List<BountyHunterEvent> getEventsForUser(String userId) {
    return repository.findEventsByUserId(userId);
  }

  public List<BountyHunterScore> getLeaderboard(int limit) {
    return repository.findTopScores(limit);
  }

  private String generateEventId() {
    return "bh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }
}
