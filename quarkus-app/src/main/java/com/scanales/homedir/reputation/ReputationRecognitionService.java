package com.scanales.homedir.reputation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ReputationRecognitionService {

  @ConfigProperty(name = "reputation.recognition.daily-limit", defaultValue = "12")
  int dailyLimit;

  @ConfigProperty(name = "reputation.recognition.cooldown-seconds", defaultValue = "300")
  long cooldownSeconds;

  @Inject ReputationFeatureFlags reputationFeatureFlags;
  @Inject ReputationEngineService reputationEngineService;

  public RecognitionResult recognize(
      String validatorUserId,
      String targetUserId,
      String sourceObjectType,
      String sourceObjectId,
      String recognitionType) {
    ReputationFeatureFlags.Flags flags = reputationFeatureFlags.snapshot();
    if (!flags.engineEnabled() || !flags.recognitionEnabled()) {
      return RecognitionResult.withDisabled();
    }

    String validator = normalizeUser(validatorUserId);
    String target = normalizeUser(targetUserId);
    String sourceType = sanitizeToken(sourceObjectType);
    String sourceId = sanitizeValue(sourceObjectId);
    RecognitionType type = RecognitionType.fromApi(recognitionType);
    if (validator == null || target == null || sourceType == null || sourceId == null || type == null) {
      return RecognitionResult.invalid("recognition_invalid_payload");
    }
    if (validator.equals(target)) {
      return RecognitionResult.invalid("recognition_self_not_allowed");
    }

    ReputationEngineService.EngineSnapshot snapshot = reputationEngineService.snapshot();
    if (isDailyLimitReached(snapshot.eventsById(), validator)) {
      return RecognitionResult.rateLimited("recognition_daily_limit_reached");
    }
    if (isCooldownActive(
        snapshot.eventsById(), validator, target, sourceType, sourceId, type.apiValue())) {
      return RecognitionResult.rateLimited("recognition_cooldown_active");
    }

    boolean tracked =
        reputationEngineService.trackRecognition(
            type.eventType(), target, sourceType, sourceId, validator, type.apiValue());
    if (!tracked) {
      return RecognitionResult.rateLimited("recognition_already_recorded");
    }
    return RecognitionResult.accepted(type.apiValue(), type.eventType());
  }

  private boolean isDailyLimitReached(
      Map<String, ReputationEventRecord> eventsById, String validatorUserId) {
    if (eventsById == null || eventsById.isEmpty() || dailyLimit <= 0) {
      return false;
    }
    Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    long count =
        eventsById.values().stream()
            .filter(Objects::nonNull)
            .filter(event -> validatorUserId.equals(normalizeUser(event.validatedByUserId())))
            .filter(event -> isRecognitionType(event.validationType()))
            .filter(event -> event.createdAt() != null)
            .filter(event -> !event.createdAt().isBefore(dayStart))
            .count();
    return count >= dailyLimit;
  }

  private boolean isCooldownActive(
      Map<String, ReputationEventRecord> eventsById,
      String validatorUserId,
      String targetUserId,
      String sourceObjectType,
      String sourceObjectId,
      String recognitionType) {
    if (eventsById == null || eventsById.isEmpty() || cooldownSeconds <= 0L) {
      return false;
    }
    Instant threshold = Instant.now().minusSeconds(cooldownSeconds);
    return eventsById.values().stream()
        .filter(Objects::nonNull)
        .filter(event -> validatorUserId.equals(normalizeUser(event.validatedByUserId())))
        .filter(event -> targetUserId.equals(normalizeUser(event.actorUserId())))
        .filter(event -> sourceObjectType.equals(sanitizeToken(event.sourceObjectType())))
        .filter(event -> sourceObjectId.equals(sanitizeValue(event.sourceObjectId())))
        .filter(event -> recognitionType.equals(sanitizeToken(event.validationType())))
        .filter(event -> event.createdAt() != null)
        .filter(event -> !event.createdAt().isBefore(threshold))
        .findFirst()
        .isPresent();
  }

  private static boolean isRecognitionType(String raw) {
    String normalized = sanitizeToken(raw);
    return "recommended".equals(normalized)
        || "helpful".equals(normalized)
        || "standout".equals(normalized);
  }

  private static String normalizeUser(String raw) {
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

  private enum RecognitionType {
    RECOMMENDED("recommended", "content_recommended"),
    HELPFUL("helpful", "peer_help_acknowledged"),
    STANDOUT("standout", "contribution_highlighted");

    private final String apiValue;
    private final String eventType;

    RecognitionType(String apiValue, String eventType) {
      this.apiValue = apiValue;
      this.eventType = eventType;
    }

    String apiValue() {
      return apiValue;
    }

    String eventType() {
      return eventType;
    }

    static RecognitionType fromApi(String raw) {
      String normalized = sanitizeToken(raw);
      if (normalized == null) {
        return null;
      }
      for (RecognitionType value : values()) {
        if (value.apiValue.equals(normalized)) {
          return value;
        }
      }
      return null;
    }
  }

  public record RecognitionResult(
      boolean accepted,
      boolean disabled,
      boolean rateLimited,
      String recognitionType,
      String eventType,
      String reason) {
    static RecognitionResult accepted(String recognitionType, String eventType) {
      return new RecognitionResult(true, false, false, recognitionType, eventType, null);
    }

    static RecognitionResult invalid(String reason) {
      return new RecognitionResult(false, false, false, null, null, reason);
    }

    static RecognitionResult rateLimited(String reason) {
      return new RecognitionResult(false, false, true, null, null, reason);
    }

    static RecognitionResult withDisabled() {
      return new RecognitionResult(false, true, false, null, null, "recognition_disabled");
    }
  }
}
