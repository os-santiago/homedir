package com.scanales.eventflow.cfp;

import com.scanales.eventflow.service.PersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class CfpEventConfigService {

  @Inject PersistenceService persistenceService;
  @Inject CfpConfigService cfpConfigService;

  private final Object lock = new Object();
  private final Map<String, CfpEventConfig> overrides = new LinkedHashMap<>();
  private volatile long lastKnownMtime = Long.MIN_VALUE;

  @PostConstruct
  void init() {
    synchronized (lock) {
      refreshFromDisk(true);
    }
  }

  public Optional<CfpEventConfig> findOverride(String eventId) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      if (normalizedEventId == null) {
        return Optional.empty();
      }
      return Optional.ofNullable(overrides.get(normalizedEventId));
    }
  }

  public ResolvedEventConfig resolveForEvent(String eventId) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      if (normalizedEventId == null) {
        throw new ValidationException("event_id_required");
      }
      CfpConfig global = cfpConfigService.current();
      CfpEventConfig override = overrides.get(normalizedEventId);
      int effectiveMax =
          override != null && override.maxSubmissionsPerUserPerEvent() != null
              ? override.maxSubmissionsPerUserPerEvent()
              : global.maxSubmissionsPerUserPerEvent();
      boolean effectiveTesting =
          override != null && override.testingModeEnabled() != null
              ? override.testingModeEnabled()
              : global.testingModeEnabled();
      boolean accepting = override == null || override.acceptingSubmissions();
      Instant opensAt = override != null ? override.opensAt() : null;
      Instant closesAt = override != null ? override.closesAt() : null;
      boolean currentlyOpen = isCurrentlyOpen(accepting, opensAt, closesAt, Instant.now());
      return new ResolvedEventConfig(
          normalizedEventId,
          override != null,
          accepting,
          opensAt,
          closesAt,
          effectiveMax,
          effectiveTesting,
          currentlyOpen,
          override != null && override.resultsPublishedAt() != null,
          override != null ? override.resultsPublishedAt() : null,
          override != null ? override.resultsPublishedBy() : null,
          override != null ? override.acceptedResultsMessage() : null,
          override != null ? override.rejectedResultsMessage() : null);
    }
  }

  public CfpEventConfig upsert(String eventId, UpdateRequest request) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      if (normalizedEventId == null) {
        throw new ValidationException("event_id_required");
      }
      if (request == null) {
        throw new ValidationException("invalid_config");
      }
      Integer normalizedLimit = normalizeLimit(request.maxSubmissionsPerUserPerEvent());
      Instant opensAt = request.opensAt();
      Instant closesAt = request.closesAt();
      if (opensAt != null && closesAt != null && !opensAt.isBefore(closesAt)) {
        throw new ValidationException("invalid_window");
      }

      CfpEventConfig updated =
          new CfpEventConfig(
              normalizedEventId,
              request.acceptingSubmissions() == null || request.acceptingSubmissions(),
              opensAt,
              closesAt,
              normalizedLimit,
              request.testingModeEnabled(),
              currentResultsPublishedAt(normalizedEventId),
              currentResultsPublishedBy(normalizedEventId),
              currentAcceptedResultsMessage(normalizedEventId),
              currentRejectedResultsMessage(normalizedEventId),
              Instant.now());
      overrides.put(normalizedEventId, updated);
      persistSync();
      return updated;
    }
  }

  public CfpEventConfig publishResults(
      String eventId, String actor, String acceptedMessage, String rejectedMessage) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      if (normalizedEventId == null) {
        throw new ValidationException("event_id_required");
      }
      String normalizedActor = sanitizeText(actor, 200);
      if (normalizedActor == null) {
        throw new ValidationException("actor_required");
      }
      CfpEventConfig current = overrides.getOrDefault(normalizedEventId, CfpEventConfig.defaults(normalizedEventId));
      CfpEventConfig updated =
          new CfpEventConfig(
              normalizedEventId,
              current.acceptingSubmissions(),
              current.opensAt(),
              current.closesAt(),
              normalizeLimit(current.maxSubmissionsPerUserPerEvent()),
              current.testingModeEnabled(),
              Instant.now(),
              normalizedActor,
              sanitizeText(acceptedMessage, 1200),
              sanitizeText(rejectedMessage, 1200),
              Instant.now());
      overrides.put(normalizedEventId, updated);
      persistSync();
      return updated;
    }
  }

  public boolean clearOverride(String eventId) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      if (normalizedEventId == null) {
        throw new ValidationException("event_id_required");
      }
      CfpEventConfig removed = overrides.remove(normalizedEventId);
      if (removed != null) {
        persistSync();
      }
      return removed != null;
    }
  }

  void resetForTests() {
    synchronized (lock) {
      overrides.clear();
      persistSync();
    }
  }

  private void persistSync() {
    persistenceService.saveCfpEventConfigsSync(new LinkedHashMap<>(overrides));
    lastKnownMtime = persistenceService.cfpEventConfigLastModifiedMillis();
  }

  private void refreshFromDisk(boolean force) {
    long mtime = persistenceService.cfpEventConfigLastModifiedMillis();
    if (!force && mtime == lastKnownMtime) {
      return;
    }
    Map<String, CfpEventConfig> loaded = persistenceService.loadCfpEventConfigs();
    overrides.clear();
    loaded.forEach(
        (eventId, config) -> {
          String normalizedEventId = sanitizeEventId(eventId);
          if (normalizedEventId == null || config == null) {
            return;
          }
          Integer normalizedLimit = normalizeLimit(config.maxSubmissionsPerUserPerEvent());
          overrides.put(
              normalizedEventId,
              new CfpEventConfig(
              normalizedEventId,
              config.acceptingSubmissions(),
              config.opensAt(),
              config.closesAt(),
              normalizedLimit,
              config.testingModeEnabled(),
              config.resultsPublishedAt(),
              config.resultsPublishedBy(),
              config.acceptedResultsMessage(),
              config.rejectedResultsMessage(),
              config.updatedAt() != null ? config.updatedAt() : Instant.now()));
        });
    lastKnownMtime = mtime;
  }

  private Instant currentResultsPublishedAt(String eventId) {
    CfpEventConfig current = overrides.get(eventId);
    return current != null ? current.resultsPublishedAt() : null;
  }

  private String currentResultsPublishedBy(String eventId) {
    CfpEventConfig current = overrides.get(eventId);
    return current != null ? current.resultsPublishedBy() : null;
  }

  private String currentAcceptedResultsMessage(String eventId) {
    CfpEventConfig current = overrides.get(eventId);
    return current != null ? current.acceptedResultsMessage() : null;
  }

  private String currentRejectedResultsMessage(String eventId) {
    CfpEventConfig current = overrides.get(eventId);
    return current != null ? current.rejectedResultsMessage() : null;
  }

  private static boolean isCurrentlyOpen(
      boolean acceptingSubmissions, Instant opensAt, Instant closesAt, Instant now) {
    if (!acceptingSubmissions) {
      return false;
    }
    if (opensAt != null && now.isBefore(opensAt)) {
      return false;
    }
    if (closesAt != null && !now.isBefore(closesAt)) {
      return false;
    }
    return true;
  }

  private static Integer normalizeLimit(Integer requestedLimit) {
    if (requestedLimit == null) {
      return null;
    }
    if (requestedLimit < CfpSubmissionService.MIN_SUBMISSIONS_PER_USER_PER_EVENT
        || requestedLimit > CfpSubmissionService.MAX_SUBMISSIONS_PER_USER_PER_EVENT) {
      throw new ValidationException("invalid_limit");
    }
    return requestedLimit;
  }

  private static String sanitizeText(String raw, int maxLength) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim();
    if (normalized.isEmpty()) {
      return null;
    }
    if (normalized.length() > maxLength) {
      normalized = normalized.substring(0, maxLength).trim();
    }
    return normalized.isEmpty() ? null : normalized;
  }

  private static String sanitizeEventId(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      return null;
    }
    StringBuilder out = new StringBuilder(normalized.length());
    boolean previousWasDash = false;
    for (int i = 0; i < normalized.length(); i++) {
      char current = normalized.charAt(i);
      boolean allowed =
          (current >= 'a' && current <= 'z')
              || (current >= '0' && current <= '9')
              || current == '_';
      if (allowed) {
        out.append(current);
        previousWasDash = false;
        continue;
      }
      if (!previousWasDash) {
        out.append('-');
        previousWasDash = true;
      }
    }

    int start = 0;
    while (start < out.length() && out.charAt(start) == '-') {
      start++;
    }
    int end = out.length();
    while (end > start && out.charAt(end - 1) == '-') {
      end--;
    }
    if (start >= end) {
      return null;
    }
    return out.substring(start, end);
  }

  public record UpdateRequest(
      Boolean acceptingSubmissions,
      Instant opensAt,
      Instant closesAt,
      Integer maxSubmissionsPerUserPerEvent,
      Boolean testingModeEnabled) {}

  public record ResolvedEventConfig(
      String eventId,
      boolean hasOverride,
      boolean acceptingSubmissions,
      Instant opensAt,
      Instant closesAt,
      int maxSubmissionsPerUserPerEvent,
      boolean testingModeEnabled,
      boolean currentlyOpen,
      boolean resultsPublished,
      Instant resultsPublishedAt,
      String resultsPublishedBy,
      String acceptedResultsMessage,
      String rejectedResultsMessage) {}

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }
}
