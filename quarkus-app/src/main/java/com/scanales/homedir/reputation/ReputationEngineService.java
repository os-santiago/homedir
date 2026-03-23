package com.scanales.homedir.reputation;

import com.scanales.homedir.service.PersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Hidden write-path engine for phase-1 Reputation Hub rollout. */
@ApplicationScoped
public class ReputationEngineService {

  private static final Duration WEEKLY_WINDOW = Duration.ofDays(7);
  private static final Duration MONTHLY_WINDOW = Duration.ofDays(30);
  private static final Duration PREVIOUS_WEEK_WINDOW = Duration.ofDays(14);

  @Inject PersistenceService persistenceService;
  @Inject ReputationFeatureFlags featureFlags;

  private final Object stateLock = new Object();
  private final Map<String, ReputationEventRecord> eventsById = new LinkedHashMap<>();
  private final Map<String, UserReputationAggregate> aggregatesByUser = new LinkedHashMap<>();
  private volatile long lastKnownStateMtime = Long.MIN_VALUE;

  @PostConstruct
  void init() {
    synchronized (stateLock) {
      refreshFromDisk(true);
    }
  }

  public boolean trackQuestCompleted(String userId, String challengeId) {
    return recordEvent("quest_completed", userId, "challenge", challengeId, null, null);
  }

  public boolean trackEventAttended(String userId, String talkId) {
    return recordEvent("event_attended", userId, "talk", talkId, null, null);
  }

  public boolean trackEventSpeaker(String userId, String submissionId, String eventId) {
    return recordEvent("event_speaker", userId, "cfp_submission", submissionId, "event", eventId);
  }

  public boolean trackContentPublished(String userId, String contentId) {
    return recordEvent("content_published", userId, "community_content", contentId, null, null);
  }

  public boolean trackRecognition(
      String eventType,
      String targetUserId,
      String sourceObjectType,
      String sourceObjectId,
      String validatorUserId,
      String validationType) {
    String normalizedValidator = normalizeUserId(validatorUserId);
    String normalizedValidationType = sanitizeToken(validationType);
    String scopeId = null;
    if (normalizedValidator != null && normalizedValidationType != null) {
      scopeId = normalizedValidator + ":" + normalizedValidationType;
    }
    return recordEvent(
        eventType,
        targetUserId,
        sourceObjectType,
        sourceObjectId,
        "recognition",
        scopeId,
        normalizedValidator,
        normalizedValidationType);
  }

  public EngineSnapshot snapshot() {
    synchronized (stateLock) {
      refreshFromDisk(false);
      return new EngineSnapshot(
          System.currentTimeMillis(),
          Map.copyOf(eventsById),
          Map.copyOf(aggregatesByUser));
    }
  }

  public UserExplainability explainUser(String userId, int limit) {
    String normalizedUserId = normalizeUserId(userId);
    int safeLimit = Math.max(1, Math.min(limit, 50));
    if (normalizedUserId == null) {
      return new UserExplainability(normalizedUserId, null, List.of(), 0L);
    }
    synchronized (stateLock) {
      refreshFromDisk(false);
      UserReputationAggregate aggregate = aggregatesByUser.get(normalizedUserId);
      List<ExplainabilityEvent> events = new ArrayList<>();
      for (ReputationEventRecord event : eventsById.values()) {
        if (event == null || !normalizedUserId.equals(event.actorUserId())) {
          continue;
        }
        events.add(toExplainabilityEvent(event));
      }
      events.sort(
          Comparator.comparing(
                  ExplainabilityEvent::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
              .thenComparing(ExplainabilityEvent::eventId, Comparator.nullsLast(String::compareTo)));
      if (events.size() > safeLimit) {
        events = events.subList(0, safeLimit);
      }
      return new UserExplainability(
          normalizedUserId,
          aggregate,
          List.copyOf(events),
          eventCountByUser(normalizedUserId));
    }
  }

  public Diagnostics diagnostics(int topLimit) {
    int safeTopLimit = Math.max(1, Math.min(topLimit, 20));
    synchronized (stateLock) {
      refreshFromDisk(false);
      Map<String, Long> eventTypeCounts = new LinkedHashMap<>();
      for (ReputationEventRecord event : eventsById.values()) {
        if (event == null || event.eventType() == null) {
          continue;
        }
        eventTypeCounts.merge(event.eventType(), 1L, Long::sum);
      }
      List<EventTypeCount> topEventTypes =
          eventTypeCounts.entrySet().stream()
              .sorted(
                  Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                      .thenComparing(Map.Entry::getKey))
              .limit(safeTopLimit)
              .map(entry -> new EventTypeCount(entry.getKey(), entry.getValue()))
              .toList();
      return new Diagnostics(
          System.currentTimeMillis(),
          eventsById.size(),
          aggregatesByUser.size(),
          List.copyOf(topEventTypes));
    }
  }

  public void resetForTests() {
    synchronized (stateLock) {
      eventsById.clear();
      aggregatesByUser.clear();
      persistenceService.saveReputationStateSync(ReputationStateSnapshot.empty());
      lastKnownStateMtime = persistenceService.reputationStateLastModifiedMillis();
    }
  }

  private boolean recordEvent(
      String eventType,
      String userId,
      String sourceObjectType,
      String sourceObjectId,
      String scopeType,
      String scopeId) {
    return recordEvent(
        eventType, userId, sourceObjectType, sourceObjectId, scopeType, scopeId, null, null);
  }

  private boolean recordEvent(
      String eventType,
      String userId,
      String sourceObjectType,
      String sourceObjectId,
      String scopeType,
      String scopeId,
      String validatedByUserId,
      String validationType) {
    if (!featureFlags.snapshot().engineEnabled()) {
      return false;
    }
    String normalizedUserId = normalizeUserId(userId);
    String normalizedSourceType = sanitizeToken(sourceObjectType);
    String normalizedSourceId = sanitizeValue(sourceObjectId);
    if (normalizedUserId == null || normalizedSourceType == null || normalizedSourceId == null) {
      return false;
    }

    ReputationEventTaxonomy.EventDefinition definition =
        ReputationEventTaxonomy.find(eventType).orElse(null);
    if (definition == null) {
      return false;
    }

    synchronized (stateLock) {
      refreshFromDisk(false);
      String eventId =
          buildEventId(
              eventType,
              normalizedUserId,
              normalizedSourceType,
              normalizedSourceId,
              sanitizeToken(scopeType),
              sanitizeValue(scopeId));
      if (eventsById.containsKey(eventId)) {
        return false;
      }
      ReputationEventRecord event =
          new ReputationEventRecord(
              eventId,
              normalizedUserId,
              sanitizeToken(eventType),
              definition.dimension().name().toLowerCase(Locale.ROOT),
              definition.dimension(),
              definition.baseWeight(),
              normalizedSourceType,
              normalizedSourceId,
              Instant.now(),
              normalizeUserId(validatedByUserId),
              sanitizeToken(validationType),
              sanitizeToken(scopeType),
              sanitizeValue(scopeId));
      eventsById.put(event.eventId(), event);
      recomputeAggregatesLocked(Instant.now());
      persistAsyncLocked();
      return true;
    }
  }

  private void recomputeAggregatesLocked(Instant now) {
    Instant weeklyStart = now.minus(WEEKLY_WINDOW);
    Instant monthlyStart = now.minus(MONTHLY_WINDOW);
    Instant previousWeeklyStart = now.minus(PREVIOUS_WEEK_WINDOW);
    Map<String, MutableAggregate> accumulators = new LinkedHashMap<>();

    for (ReputationEventRecord event : eventsById.values()) {
      if (event == null || event.actorUserId() == null || event.weightBase() <= 0) {
        continue;
      }
      MutableAggregate aggregate =
          accumulators.computeIfAbsent(event.actorUserId(), ignored -> new MutableAggregate());
      long weight = event.weightBase();
      aggregate.totalScore += weight;
      aggregate.dimensionScores.merge(
          event.dimension().name().toLowerCase(Locale.ROOT), weight, Long::sum);
      Instant createdAt = event.createdAt() == null ? now : event.createdAt();
      if (!createdAt.isBefore(weeklyStart)) {
        aggregate.weeklyScore += weight;
      }
      if (!createdAt.isBefore(monthlyStart)) {
        aggregate.monthlyScore += weight;
      }
      if (!createdAt.isBefore(previousWeeklyStart) && createdAt.isBefore(weeklyStart)) {
        aggregate.previousWeeklyScore += weight;
      }
    }

    aggregatesByUser.clear();
    for (Map.Entry<String, MutableAggregate> entry : accumulators.entrySet()) {
      MutableAggregate value = entry.getValue();
      aggregatesByUser.put(
          entry.getKey(),
          new UserReputationAggregate(
              entry.getKey(),
              value.totalScore,
              value.dimensionScores,
              value.weeklyScore,
              value.monthlyScore,
              value.weeklyScore - value.previousWeeklyScore,
              now));
    }
  }

  private void persistAsyncLocked() {
    persistenceService.saveReputationState(toSnapshot());
    lastKnownStateMtime = persistenceService.reputationStateLastModifiedMillis();
  }

  private ReputationStateSnapshot toSnapshot() {
    return new ReputationStateSnapshot(
        ReputationStateSnapshot.SCHEMA_VERSION,
        Instant.now(),
        new LinkedHashMap<>(eventsById),
        new LinkedHashMap<>(aggregatesByUser));
  }

  private void refreshFromDisk(boolean force) {
    long diskMtime = persistenceService.reputationStateLastModifiedMillis();
    if (!force && diskMtime == lastKnownStateMtime) {
      return;
    }
    ReputationStateSnapshot snapshot =
        persistenceService.loadReputationState().orElse(ReputationStateSnapshot.empty());
    eventsById.clear();
    eventsById.putAll(snapshot.events());
    aggregatesByUser.clear();
    aggregatesByUser.putAll(snapshot.aggregatesByUser());
    if (aggregatesByUser.isEmpty() && !eventsById.isEmpty()) {
      recomputeAggregatesLocked(Instant.now());
      persistenceService.saveReputationStateSync(toSnapshot());
      diskMtime = persistenceService.reputationStateLastModifiedMillis();
    }
    lastKnownStateMtime = diskMtime;
  }

  private static String buildEventId(
      String eventType,
      String userId,
      String sourceObjectType,
      String sourceObjectId,
      String scopeType,
      String scopeId) {
    String composite =
        String.join(
            "|",
            sanitizeToken(eventType),
            userId == null ? "unknown" : userId,
            sourceObjectType == null ? "source" : sourceObjectType,
            sourceObjectId == null ? "id" : sourceObjectId,
            scopeType == null ? "-" : scopeType,
            scopeId == null ? "-" : scopeId);
    return UUID.nameUUIDFromBytes(composite.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .toString();
  }

  private static String normalizeUserId(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  private static String sanitizeToken(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]", "_");
    return normalized.isBlank() ? null : normalized;
  }

  private static String sanitizeValue(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim();
  }

  public record EngineSnapshot(
      long generatedAtMillis,
      Map<String, ReputationEventRecord> eventsById,
      Map<String, UserReputationAggregate> aggregatesByUser) {}

  public record UserExplainability(
      String userId,
      UserReputationAggregate aggregate,
      List<ExplainabilityEvent> recentEvents,
      long eventCount) {}

  public record ExplainabilityEvent(
      String eventId,
      String eventType,
      String dimension,
      String impactBand,
      String sourceObjectType,
      String sourceObjectId,
      Instant occurredAt,
      String reason) {}

  public record Diagnostics(
      long generatedAtMillis, long totalEvents, long totalUsers, List<EventTypeCount> topEventTypes) {}

  public record EventTypeCount(String eventType, long count) {}

  private ExplainabilityEvent toExplainabilityEvent(ReputationEventRecord event) {
    ReputationEventTaxonomy.EventDefinition definition =
        ReputationEventTaxonomy.find(event.eventType()).orElse(null);
    String reason =
        definition != null && definition.rationale() != null && !definition.rationale().isBlank()
            ? definition.rationale()
            : "Meaningful contribution signal recorded.";
    return new ExplainabilityEvent(
        event.eventId(),
        event.eventType(),
        event.dimension() == null ? null : event.dimension().name().toLowerCase(Locale.ROOT),
        impactBand(event.weightBase()),
        event.sourceObjectType(),
        event.sourceObjectId(),
        event.createdAt(),
        reason);
  }

  private long eventCountByUser(String userId) {
    return eventsById.values().stream()
        .filter(Objects::nonNull)
        .filter(event -> userId.equals(event.actorUserId()))
        .count();
  }

  private static String impactBand(int weightBase) {
    if (weightBase >= 15) {
      return "high";
    }
    if (weightBase >= 10) {
      return "medium";
    }
    return "low";
  }

  private static final class MutableAggregate {
    long totalScore;
    long weeklyScore;
    long monthlyScore;
    long previousWeeklyScore;
    Map<String, Long> dimensionScores = new LinkedHashMap<>();
  }
}
