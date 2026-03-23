package com.scanales.homedir.reputation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Phase-3 read model for public profile reputation summary. */
@ApplicationScoped
public class ReputationProfileSummaryService {

  @Inject ReputationFeatureFlags featureFlags;
  @Inject ReputationEngineService reputationEngineService;

  public Optional<PublicProfileSummary> summaryForUser(String userId) {
    ReputationFeatureFlags.Flags flags = featureFlags.snapshot();
    if (!flags.engineEnabled() || !flags.profileSummaryEnabled()) {
      return Optional.empty();
    }
    String normalizedUserId = normalize(userId);
    if (normalizedUserId == null) {
      return Optional.empty();
    }

    ReputationEngineService.EngineSnapshot snapshot = reputationEngineService.snapshot();
    UserReputationAggregate aggregate = snapshot.aggregatesByUser().get(normalizedUserId);
    if (aggregate == null || aggregate.totalScore() <= 0) {
      return Optional.empty();
    }

    List<String> topStrengths = topStrengths(aggregate.scoresByDimension());
    return Optional.of(
        new PublicProfileSummary(
            normalizedUserId,
            reputationState(aggregate.totalScore()),
            aggregate.totalScore(),
            monthlyRank(snapshot.aggregatesByUser(), normalizedUserId),
            List.copyOf(topStrengths),
            List.copyOf(badgesPreview(aggregate)),
            topStrengths.isEmpty() ? "contributor" : topStrengths.get(0),
            latestMilestone(aggregate)));
  }

  private static long monthlyRank(
      Map<String, UserReputationAggregate> aggregatesByUser, String userId) {
    if (aggregatesByUser == null || aggregatesByUser.isEmpty()) {
      return 1L;
    }
    List<UserReputationAggregate> ordered =
        aggregatesByUser.values().stream()
            .filter(Objects::nonNull)
            .sorted(
                Comparator.comparingLong(UserReputationAggregate::monthlyScore)
                    .reversed()
                    .thenComparing(
                        Comparator.comparingLong(UserReputationAggregate::totalScore).reversed())
                    .thenComparing(UserReputationAggregate::userId, Comparator.nullsLast(String::compareTo)))
            .toList();
    for (int i = 0; i < ordered.size(); i++) {
      UserReputationAggregate row = ordered.get(i);
      if (row != null && userId.equals(row.userId())) {
        return i + 1L;
      }
    }
    return ordered.size() + 1L;
  }

  private static List<String> topStrengths(Map<String, Long> scoresByDimension) {
    if (scoresByDimension == null || scoresByDimension.isEmpty()) {
      return List.of();
    }
    return scoresByDimension.entrySet().stream()
        .filter(entry -> entry.getValue() != null && entry.getValue() > 0L)
        .sorted(
            Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey))
        .map(entry -> normalizeDimension(entry.getKey()))
        .filter(Objects::nonNull)
        .limit(3)
        .toList();
  }

  private static String normalizeDimension(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "participation", "contribution", "recognition", "consistency" -> normalized;
      default -> null;
    };
  }

  private static List<String> badgesPreview(UserReputationAggregate aggregate) {
    long participation = score(aggregate, "participation");
    long contribution = score(aggregate, "contribution");
    long recognition = score(aggregate, "recognition");

    List<String> badges = new ArrayList<>();
    if (aggregate.risingDelta() >= 8L) {
      badges.add("rising_member");
    }
    if (aggregate.monthlyScore() >= 35L) {
      badges.add("consistent_contributor");
    }
    if (recognition >= 20L) {
      badges.add("helpful_voice");
    }
    if (contribution >= 25L) {
      badges.add("recognized_builder");
    }
    if (participation >= 20L) {
      badges.add("active_learner");
    }
    if (aggregate.totalScore() >= 80L) {
      badges.add("community_supporter");
    }
    if (badges.size() > 3) {
      return List.copyOf(badges.subList(0, 3));
    }
    return List.copyOf(badges);
  }

  private static long score(UserReputationAggregate aggregate, String dimension) {
    if (aggregate == null
        || aggregate.scoresByDimension() == null
        || dimension == null
        || dimension.isBlank()) {
      return 0L;
    }
    return Math.max(0L, aggregate.scoresByDimension().getOrDefault(dimension, 0L));
  }

  private static String reputationState(long totalScore) {
    if (totalScore < 20L) {
      return "emerging";
    }
    if (totalScore < 45L) {
      return "active";
    }
    if (totalScore < 80L) {
      return "recognized";
    }
    if (totalScore < 140L) {
      return "trusted";
    }
    return "impactful";
  }

  private static Milestone latestMilestone(UserReputationAggregate aggregate) {
    if (aggregate.risingDelta() > 0L) {
      return new Milestone("rising_delta", aggregate.risingDelta());
    }
    if (aggregate.monthlyScore() > 0L) {
      return new Milestone("monthly_score", aggregate.monthlyScore());
    }
    return new Milestone("total_score", aggregate.totalScore());
  }

  private static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  public record PublicProfileSummary(
      String userId,
      String reputationState,
      long totalScore,
      long monthlyRank,
      List<String> topStrengths,
      List<String> badgesPreview,
      String knownFor,
      Milestone milestone) {}

  public record Milestone(String type, long value) {}
}
