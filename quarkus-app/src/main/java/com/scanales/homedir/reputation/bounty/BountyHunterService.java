package com.scanales.homedir.reputation.bounty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for the Bounty Hunter program. Handles scoring, level progression, and event
 * tracking.
 */
@ApplicationScoped
public class BountyHunterService {

  @Inject BountyHunterRepository repository;
  @Inject BountyHunterConfigService configService;

  public BountyHunterScore awardIssueCreationPoints(
      String userId, String issueNumber, String label, String validatedByUserId) {
    String normalizedUserId = normalizeUserId(userId);

    long points = configService.getPointsForLabel(label);
    if (points <= 0) {
      throw new IllegalArgumentException("Label '" + label + "' is not eligible for points");
    }

    if (!configService.isAdminUser(validatedByUserId)) {
      throw new IllegalArgumentException(
          "User '" + validatedByUserId + "' is not authorized to validate issues");
    }

    return repository.awardAtomically(
        normalizedUserId,
        issueNumber,
        BountyHunterEventType.ISSUE_LABEL_APPROVED,
        optScore -> optScore.orElse(new BountyHunterScore(normalizedUserId, 0L, 0L, 0L, BountyHunterLevel.NONE, 0, 0, Instant.now()))
                            .withAddedIssueCreationPoints(points, issueNumber),
        () -> new BountyHunterEvent(
            generateEventId(),
            normalizedUserId,
            BountyHunterEventType.ISSUE_LABEL_APPROVED,
            issueNumber,
            null,
            points,
            label,
            validatedByUserId,
            Instant.now())
    );
  }

  /** Award points for issue resolution via approved PR. */
  public BountyHunterScore awardIssueResolutionPoints(
      String userId, String issueNumber, String prNumber, String label) {
    String normalizedUserId = normalizeUserId(userId);

    long points = configService.getPointsForLabel(label);
    if (points <= 0) {
      throw new IllegalArgumentException("Label '" + label + "' is not eligible for points");
    }

    return repository.awardAtomically(
        normalizedUserId,
        issueNumber,
        BountyHunterEventType.ISSUE_RESOLVED_BY_PR,
        optScore -> optScore.orElse(new BountyHunterScore(normalizedUserId, 0L, 0L, 0L, BountyHunterLevel.NONE, 0, 0, Instant.now()))
                            .withAddedIssueResolutionPoints(points, issueNumber),
        () -> new BountyHunterEvent(
            generateEventId(),
            normalizedUserId,
            BountyHunterEventType.ISSUE_RESOLVED_BY_PR,
            issueNumber,
            prNumber,
            points,
            label,
            null,
            Instant.now())
    );
  }

  public Optional<BountyHunterScore> getScoreForUser(String userId) {
    return repository.findScoreByUserId(normalizeUserId(userId));
  }

  public Optional<BountyHunterScore> getUserScore(String userId) {
    return getScoreForUser(userId);
  }

  public List<BountyHunterEvent> getEventsForUser(String userId) {
    return repository.findEventsByUserId(normalizeUserId(userId));
  }

  public List<BountyHunterEvent> getUserHistory(String userId) {
    return getEventsForUser(userId);
  }

  public Optional<Integer> getUserRank(String userId) {
    String normalizedUserId = normalizeUserId(userId);
    if (normalizedUserId == null) {
      return Optional.empty();
    }
    List<BountyHunterScore> leaderboard = repository.findTopScores(Integer.MAX_VALUE);
    for (int index = 0; index < leaderboard.size(); index++) {
      if (normalizedUserId.equals(leaderboard.get(index).userId())) {
        return Optional.of(index + 1);
      }
    }
    return Optional.empty();
  }

  public long getTotalHuntersCount() {
    return repository.countScores();
  }

  public Optional<BountyHunterScore> validateIssue(
      String userId, String issueNumber, String label, String validatedByUserId) {
    try {
      return Optional.of(awardIssueCreationPoints(userId, issueNumber, label, validatedByUserId));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public BountyHunterScore recordIssueResolution(
      String userId, String issueNumber, String prNumber, String label) {
    return awardIssueResolutionPoints(userId, issueNumber, prNumber, label);
  }

  public List<BountyHunterScore> getLeaderboard(int limit) {
    return repository.findTopScores(limit);
  }

  private String generateEventId() {
    return "bh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }

  private static String normalizeUserId(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim().toLowerCase(java.util.Locale.ROOT);
  }
}
