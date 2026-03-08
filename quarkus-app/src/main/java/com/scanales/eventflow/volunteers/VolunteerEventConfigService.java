package com.scanales.eventflow.volunteers;

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
public class VolunteerEventConfigService {

  @Inject PersistenceService persistenceService;

  private final Object lock = new Object();
  private final Map<String, VolunteerEventConfig> overrides = new LinkedHashMap<>();
  private volatile long lastKnownMtime = Long.MIN_VALUE;

  @PostConstruct
  void init() {
    synchronized (lock) {
      refreshFromDisk(true);
    }
  }

  public Optional<VolunteerEventConfig> findOverride(String eventId) {
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
      VolunteerEventConfig override = overrides.get(normalizedEventId);
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
          currentlyOpen);
    }
  }

  public VolunteerEventConfig upsert(String eventId, UpdateRequest request) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      if (normalizedEventId == null) {
        throw new ValidationException("event_id_required");
      }
      if (request == null) {
        throw new ValidationException("invalid_config");
      }
      Instant opensAt = request.opensAt();
      Instant closesAt = request.closesAt();
      if (opensAt != null && closesAt != null && !opensAt.isBefore(closesAt)) {
        throw new ValidationException("invalid_window");
      }
      VolunteerEventConfig updated =
          new VolunteerEventConfig(
              normalizedEventId,
              request.acceptingSubmissions() == null || request.acceptingSubmissions(),
              opensAt,
              closesAt,
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
      VolunteerEventConfig removed = overrides.remove(normalizedEventId);
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
    persistenceService.saveVolunteerEventConfigsSync(new LinkedHashMap<>(overrides));
    lastKnownMtime = persistenceService.volunteerEventConfigLastModifiedMillis();
  }

  private void refreshFromDisk(boolean force) {
    long mtime = persistenceService.volunteerEventConfigLastModifiedMillis();
    if (!force && mtime == lastKnownMtime) {
      return;
    }
    Map<String, VolunteerEventConfig> loaded = persistenceService.loadVolunteerEventConfigs();
    overrides.clear();
    loaded.forEach(
        (eventId, config) -> {
          String normalizedEventId = sanitizeEventId(eventId);
          if (normalizedEventId == null || config == null) {
            return;
          }
          overrides.put(
              normalizedEventId,
              new VolunteerEventConfig(
                  normalizedEventId,
                  config.acceptingSubmissions(),
                  config.opensAt(),
                  config.closesAt(),
                  config.updatedAt() != null ? config.updatedAt() : Instant.now()));
        });
    lastKnownMtime = mtime;
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

  public record UpdateRequest(Boolean acceptingSubmissions, Instant opensAt, Instant closesAt) {}

  public record ResolvedEventConfig(
      String eventId,
      boolean hasOverride,
      boolean acceptingSubmissions,
      Instant opensAt,
      Instant closesAt,
      boolean currentlyOpen) {}

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }
}