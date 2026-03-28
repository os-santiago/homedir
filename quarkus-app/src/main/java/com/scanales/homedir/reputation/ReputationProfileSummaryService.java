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
    String reputationRole = reputationRole(snapshot, normalizedUserId, aggregate);
    List<Placement> activePlacements =
        activePlacements(snapshot, normalizedUserId, aggregate, reputationRole);
    List<RecognizedSignal> recognizedSignals = recognizedSignals(snapshot, normalizedUserId);
    return Optional.of(
        new PublicProfileSummary(
            normalizedUserId,
            reputationState(aggregate.totalScore()),
            aggregate.totalScore(),
            monthlyRank(snapshot.aggregatesByUser(), normalizedUserId),
            List.copyOf(activePlacements),
            List.copyOf(topStrengths),
            List.copyOf(badgesPreview(aggregate)),
            recognizedSignals.size(),
            List.copyOf(recognizedSignals),
            topStrengths.isEmpty() ? "contributor" : topStrengths.get(0),
            reputationRole,
            latestMilestone(aggregate)));
  }

  private static List<RecognizedSignal> recognizedSignals(
      ReputationEngineService.EngineSnapshot snapshot, String userId) {
    if (snapshot == null || snapshot.eventsById() == null || userId == null || userId.isBlank()) {
      return List.of();
    }
    return snapshot.eventsById().values().stream()
        .filter(Objects::nonNull)
        .filter(event -> userId.equals(event.actorUserId()))
        .filter(ReputationProfileSummaryService::isRecognitionEvent)
        .sorted(
            Comparator.comparing(
                    ReputationEventRecord::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                    Comparator.comparingInt(ReputationEventRecord::weightBase).reversed())
                .thenComparing(
                    ReputationEventRecord::eventId, Comparator.nullsLast(String::compareTo)))
        .limit(3)
        .map(
            event ->
                new RecognizedSignal(
                    recognitionLabel(event), recognitionEventKey(event.eventType())))
        .toList();
  }

  private static boolean isRecognitionEvent(ReputationEventRecord event) {
    if (event == null) {
      return false;
    }
    String eventType = normalize(event.eventType());
    String validationType = normalize(event.validationType());
    if ("recommended".equals(validationType)
        || "helpful".equals(validationType)
        || "standout".equals(validationType)) {
      return true;
    }
    return "content_recommended".equals(eventType)
        || "peer_help_acknowledged".equals(eventType)
        || "contribution_highlighted".equals(eventType);
  }

  private static String recognitionLabel(ReputationEventRecord event) {
    String validationType = normalize(event != null ? event.validationType() : null);
    if ("recommended".equals(validationType)
        || "helpful".equals(validationType)
        || "standout".equals(validationType)) {
      return validationType;
    }
    int weight = event == null ? 0 : event.weightBase();
    if (weight >= 15) {
      return "standout";
    }
    if (weight >= 10) {
      return "recommended";
    }
    return "helpful";
  }

  private static String recognitionEventKey(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      return "activity_signal";
    }
    return switch (eventType) {
      case "content_recommended" -> "content_recommended";
      case "peer_help_acknowledged" -> "peer_help_acknowledged";
      case "contribution_highlighted" -> "contribution_highlighted";
      default -> "activity_signal";
    };
  }

  private static List<Placement> activePlacements(
      ReputationEngineService.EngineSnapshot snapshot,
      String userId,
      UserReputationAggregate aggregate,
      String reputationRole) {
    if (snapshot == null || snapshot.aggregatesByUser() == null || aggregate == null) {
      return List.of();
    }

    List<Placement> placements = new ArrayList<>();

    String categoryKey = categoryKeyForRole(reputationRole);
    if (categoryKey != null) {
      long categoryRank =
          switch (categoryKey) {
            case "builders" ->
                rankBy(
                    snapshot.aggregatesByUser(),
                    userId,
                    row -> score(row, "contribution"),
                    UserReputationAggregate::monthlyScore);
            case "helpers" ->
                rankBy(
                    snapshot.aggregatesByUser(),
                    userId,
                    row -> score(row, "recognition"),
                    UserReputationAggregate::monthlyScore);
            case "learners" ->
                rankBy(
                    snapshot.aggregatesByUser(),
                    userId,
                    row -> score(row, "participation") + score(row, "consistency"),
                    UserReputationAggregate::monthlyScore);
            case "speakers" ->
                rankBy(
                    snapshot.aggregatesByUser(),
                    userId,
                    row -> speakerScore(snapshot, row.userId()),
                    UserReputationAggregate::monthlyScore);
            default -> 0L;
          };
      if (categoryRank > 0 && categoryRank <= 10) {
        placements.add(new Placement("category", categoryRank, categoryKey));
      }
    }

    long weeklyRank =
        rankBy(
            snapshot.aggregatesByUser(),
            userId,
            UserReputationAggregate::weeklyScore,
            UserReputationAggregate::totalScore);
    if (weeklyRank > 0 && weeklyRank <= 10) {
      placements.add(new Placement("weekly", weeklyRank, null));
    }

    if (aggregate.risingDelta() > 0L) {
      long risingRank =
          rankBy(
              snapshot.aggregatesByUser(),
              userId,
              UserReputationAggregate::risingDelta,
              UserReputationAggregate::totalScore);
      if (risingRank > 0 && risingRank <= 10) {
        placements.add(new Placement("rising", risingRank, null));
      }
    }

    if (placements.isEmpty() && aggregate.monthlyScore() > 0L) {
      long monthlyRank = monthlyRank(snapshot.aggregatesByUser(), userId);
      if (monthlyRank > 0 && monthlyRank <= 10) {
        placements.add(new Placement("monthly", monthlyRank, null));
      }
    }

    return placements.size() > 3 ? List.copyOf(placements.subList(0, 3)) : List.copyOf(placements);
  }

  private static long monthlyRank(
      Map<String, UserReputationAggregate> aggregatesByUser, String userId) {
    return rankBy(
        aggregatesByUser,
        userId,
        UserReputationAggregate::monthlyScore,
        UserReputationAggregate::totalScore);
  }

  private static long rankBy(
      Map<String, UserReputationAggregate> aggregatesByUser,
      String userId,
      java.util.function.ToLongFunction<UserReputationAggregate> scoreExtractor,
      java.util.function.ToLongFunction<UserReputationAggregate> tieBreaker) {
    if (aggregatesByUser == null || aggregatesByUser.isEmpty()) {
      return 1L;
    }
    List<UserReputationAggregate> ordered =
        aggregatesByUser.values().stream()
            .filter(Objects::nonNull)
            .filter(aggregate -> scoreExtractor.applyAsLong(aggregate) > 0L)
            .sorted(
                Comparator.comparingLong(scoreExtractor)
                    .reversed()
                    .thenComparing(Comparator.comparingLong(tieBreaker).reversed())
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

  static String normalizeDimension(String raw) {
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

  static String reputationRole(
      ReputationEngineService.EngineSnapshot snapshot,
      String userId,
      UserReputationAggregate aggregate) {
    long participation = score(aggregate, "participation");
    long contribution = score(aggregate, "contribution");
    long recognition = score(aggregate, "recognition");
    long consistency = score(aggregate, "consistency");
    long speakerScore = speakerScore(snapshot, userId);

    if (speakerScore >= 14L) {
      return "speaker";
    }
    if (recognition >= 20L && recognition >= contribution) {
      return "helper";
    }
    if (contribution >= 25L) {
      return "builder";
    }
    if (participation + consistency >= 20L) {
      return "learner";
    }
    if (aggregate.monthlyScore() >= 35L || consistency >= 8L) {
      return "consistent_contributor";
    }
    return "contributor";
  }

  private static long speakerScore(ReputationEngineService.EngineSnapshot snapshot, String userId) {
    if (snapshot == null || snapshot.eventsById() == null || userId == null || userId.isBlank()) {
      return 0L;
    }
    return snapshot.eventsById().values().stream()
        .filter(Objects::nonNull)
        .filter(event -> userId.equals(event.actorUserId()))
        .filter(event -> "event_speaker".equals(event.eventType()))
        .mapToLong(ReputationEventRecord::weightBase)
        .sum();
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

  private static String categoryKeyForRole(String reputationRole) {
    if (reputationRole == null || reputationRole.isBlank()) {
      return null;
    }
    return switch (reputationRole) {
      case "builder" -> "builders";
      case "helper" -> "helpers";
      case "learner" -> "learners";
      case "speaker" -> "speakers";
      default -> null;
    };
  }

  public record PublicProfileSummary(
      String userId,
      String reputationState,
      long totalScore,
      long monthlyRank,
      List<Placement> activePlacements,
      List<String> topStrengths,
      List<String> badgesPreview,
      int recognizedSignalsCount,
      List<RecognizedSignal> recognizedSignals,
      String knownFor,
      String reputationRole,
      Milestone milestone) {}

  public record Placement(String type, long rank, String categoryKey) {}

  public record RecognizedSignal(String recognitionLabel, String eventKey) {}

  public record Milestone(String type, long value) {}
}
