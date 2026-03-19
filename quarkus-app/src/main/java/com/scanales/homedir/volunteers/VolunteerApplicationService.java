package com.scanales.homedir.volunteers;

import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.PersistenceService;
import com.scanales.homedir.util.PaginationGuardrails;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@ApplicationScoped
public class VolunteerApplicationService {
  private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
  private static final int MIN_RATING = 0;
  private static final int MAX_RATING = 5;

  public enum SortOrder {
    CREATED_DESC,
    UPDATED_DESC,
    SCORE_DESC
  }

  @Inject PersistenceService persistenceService;
  @Inject EventService eventService;
  @Inject VolunteerEventConfigService volunteerEventConfigService;

  private final ConcurrentHashMap<String, VolunteerApplication> applications = new ConcurrentHashMap<>();
  private final Object lock = new Object();
  private volatile long lastKnownMtime = Long.MIN_VALUE;

  @PostConstruct
  void init() {
    synchronized (lock) {
      refreshFromDisk(true);
    }
  }

  public VolunteerApplication create(String userId, String userName, CreateRequest request) {
    synchronized (lock) {
      refreshFromDisk(false);
      if (request == null) {
        throw new ValidationException("request_required");
      }
      String eventId = sanitizeId(request.eventId());
      if (eventId == null) {
        throw new ValidationException("event_id_required");
      }
      if (eventService.getEvent(eventId) == null) {
        throw new NotFoundException("event_not_found");
      }
      String applicantId = sanitizeUserId(userId);
      if (applicantId == null) {
        throw new ValidationException("user_id_required");
      }
      VolunteerEventConfigService.ResolvedEventConfig eventConfig =
          resolveEventConfig(eventId);
      if (!eventConfig.currentlyOpen()) {
        throw new ValidationException("submissions_closed");
      }
      if (findByEventAndUser(eventId, applicantId).isPresent()) {
        throw new ValidationException("already_applied");
      }
      String aboutMe = sanitizeText(request.aboutMe(), 1500);
      String joinReason = sanitizeText(request.joinReason(), 1500);
      String differentiator = sanitizeText(request.differentiator(), 500);
      if (aboutMe == null) {
        throw new ValidationException("invalid_about_me");
      }
      if (joinReason == null) {
        throw new ValidationException("invalid_join_reason");
      }
      Instant now = Instant.now();
      VolunteerApplication created =
          new VolunteerApplication(
              UUID.randomUUID().toString(),
              eventId,
              applicantId,
              sanitizeText(userName, 120),
              aboutMe,
              joinReason,
              differentiator,
              VolunteerApplicationStatus.APPLIED,
              now,
              now,
              null,
              null,
              null,
              null,
              null,
              null);
      applications.put(created.id(), created);
      persistSync();
      return created;
    }
  }

  public VolunteerApplication updateMine(String id, String eventId, String userId, UpdateRequest request) {
    synchronized (lock) {
      refreshFromDisk(false);
      VolunteerApplication current = findOrThrow(id);
      String normalizedEventId = sanitizeId(eventId);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedEventId == null || normalizedUserId == null) {
        throw new ValidationException("owner_required");
      }
      if (!normalizedEventId.equals(current.eventId()) || !normalizedUserId.equals(current.applicantUserId())) {
        throw new ValidationException("owner_required");
      }
      if (request == null) {
        throw new ValidationException("request_required");
      }
      if (current.status() == VolunteerApplicationStatus.SELECTED
          || current.status() == VolunteerApplicationStatus.NOT_SELECTED) {
        throw new InvalidTransitionException("immutable_after_decision");
      }
      String aboutMe = sanitizeText(request.aboutMe(), 1500);
      String joinReason = sanitizeText(request.joinReason(), 1500);
      String differentiator = sanitizeText(request.differentiator(), 500);
      if (aboutMe == null) {
        throw new ValidationException("invalid_about_me");
      }
      if (joinReason == null) {
        throw new ValidationException("invalid_join_reason");
      }
      Instant now = nextUpdatedAt(current);
      VolunteerApplication updated =
          new VolunteerApplication(
              current.id(),
              current.eventId(),
              current.applicantUserId(),
              current.applicantName(),
              aboutMe,
              joinReason,
              differentiator,
              current.status(),
              current.createdAt(),
              now,
              current.moderatedAt(),
              current.moderatedBy(),
              current.moderationNote(),
              current.ratingProfile(),
              current.ratingMotivation(),
              current.ratingDifferentiator());
      applications.put(updated.id(), updated);
      persistSync();
      return updated;
    }
  }

  public VolunteerApplication withdrawMine(String id, String eventId, String userId) {
    synchronized (lock) {
      refreshFromDisk(false);
      VolunteerApplication current = findOrThrow(id);
      String normalizedEventId = sanitizeId(eventId);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedEventId == null || normalizedUserId == null) {
        throw new ValidationException("owner_required");
      }
      if (!normalizedEventId.equals(current.eventId()) || !normalizedUserId.equals(current.applicantUserId())) {
        throw new ValidationException("owner_required");
      }
      if (!isTransitionAllowed(current.status(), VolunteerApplicationStatus.WITHDRAWN)) {
        throw new InvalidTransitionException("invalid_status_transition");
      }
      return updateStatusInternal(
          current,
          VolunteerApplicationStatus.WITHDRAWN,
          sanitizeText(current.applicantName(), 120),
          current.moderationNote());
    }
  }

  public VolunteerApplication updateStatus(
      String id,
      VolunteerApplicationStatus newStatus,
      String moderator,
      String note,
      Instant expectedUpdatedAt) {
    synchronized (lock) {
      refreshFromDisk(false);
      if (newStatus == null) {
        throw new ValidationException("status_required");
      }
      VolunteerApplication current = findOrThrow(id);
      validateExpectedUpdatedAt(current, expectedUpdatedAt);
      if (!isTransitionAllowed(current.status(), newStatus)) {
        throw new InvalidTransitionException("invalid_status_transition");
      }
      String normalizedNote = sanitizeText(note, 500);
      if (newStatus == VolunteerApplicationStatus.NOT_SELECTED
          && (normalizedNote == null || normalizedNote.isBlank())) {
        throw new ValidationException("reject_note_required");
      }
      return updateStatusInternal(current, newStatus, sanitizeText(moderator, 200), normalizedNote);
    }
  }

  public VolunteerApplication updateRating(
      String eventId,
      String id,
      Integer profile,
      Integer motivation,
      Integer differentiator,
      String moderator,
      Instant expectedUpdatedAt) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        throw new NotFoundException("application_not_found");
      }
      VolunteerApplication current = findOrThrow(id);
      if (!normalizedEventId.equals(current.eventId())) {
        throw new NotFoundException("application_not_found");
      }
      validateExpectedUpdatedAt(current, expectedUpdatedAt);
      int normalizedProfile = normalizeRating(profile, "invalid_rating_profile");
      int normalizedMotivation = normalizeRating(motivation, "invalid_rating_motivation");
      int normalizedDifferentiator = normalizeRating(differentiator, "invalid_rating_differentiator");
      Instant now = nextUpdatedAt(current);
      VolunteerApplication updated =
          new VolunteerApplication(
              current.id(),
              current.eventId(),
              current.applicantUserId(),
              current.applicantName(),
              current.aboutMe(),
              current.joinReason(),
              current.differentiator(),
              current.status(),
              current.createdAt(),
              now,
              current.moderatedAt(),
              sanitizeText(moderator, 200),
              current.moderationNote(),
              normalizedProfile,
              normalizedMotivation,
              normalizedDifferentiator);
      applications.put(updated.id(), updated);
      persistSync();
      return updated;
    }
  }

  public Optional<VolunteerApplication> findById(String id) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalized = sanitizeId(id);
      if (normalized == null) {
        return Optional.empty();
      }
      return Optional.ofNullable(applications.get(normalized));
    }
  }

  public Optional<VolunteerApplication> findByEventAndUser(String eventId, String userId) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedEventId == null || normalizedUserId == null) {
        return Optional.empty();
      }
      return applications.values().stream()
          .filter(item -> normalizedEventId.equals(item.eventId()))
          .filter(item -> normalizedUserId.equals(item.applicantUserId()))
          .findFirst();
    }
  }

  public List<VolunteerApplication> listByEvent(
      String eventId,
      Optional<VolunteerApplicationStatus> statusFilter,
      SortOrder sortOrder,
      int requestedLimit,
      int requestedOffset) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        return List.of();
      }
      List<VolunteerApplication> filtered =
          applications.values().stream()
              .filter(item -> normalizedEventId.equals(item.eventId()))
              .filter(item -> statusFilter.isEmpty() || item.status() == statusFilter.get())
              .sorted(sortComparator(sortOrder))
              .toList();
      return paginate(filtered, requestedLimit, requestedOffset);
    }
  }

  public List<VolunteerApplication> listMineAcrossEvents(
      Set<String> userIds, SortOrder sortOrder, int requestedLimit, int requestedOffset) {
    synchronized (lock) {
      refreshFromDisk(false);
      Set<String> normalized = normalizeUserIds(userIds);
      if (normalized.isEmpty()) {
        return List.of();
      }
      List<VolunteerApplication> filtered =
          applications.values().stream()
              .filter(item -> normalized.contains(item.applicantUserId()))
              .sorted(sortComparator(sortOrder))
              .toList();
      return paginate(filtered, requestedLimit, requestedOffset);
    }
  }

  public int countByEvent(String eventId, Optional<VolunteerApplicationStatus> statusFilter) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        return 0;
      }
      int count = 0;
      for (VolunteerApplication item : applications.values()) {
        if (!normalizedEventId.equals(item.eventId())) {
          continue;
        }
        if (statusFilter.isPresent() && item.status() != statusFilter.get()) {
          continue;
        }
        count++;
      }
      return count;
    }
  }

  public MineStats statsMineAcrossEvents(Set<String> userIds) {
    synchronized (lock) {
      refreshFromDisk(false);
      Set<String> normalized = normalizeUserIds(userIds);
      if (normalized.isEmpty()) {
        return MineStats.empty();
      }
      EnumMap<VolunteerApplicationStatus, Integer> counts =
          new EnumMap<>(VolunteerApplicationStatus.class);
      Set<String> distinctEvents = new LinkedHashSet<>();
      int total = 0;
      Instant latestUpdatedAt = null;
      for (VolunteerApplication item : applications.values()) {
        if (!normalized.contains(item.applicantUserId())) {
          continue;
        }
        total++;
        if (item.eventId() != null && !item.eventId().isBlank()) {
          distinctEvents.add(item.eventId());
        }
        VolunteerApplicationStatus status =
            item.status() != null ? item.status() : VolunteerApplicationStatus.APPLIED;
        counts.merge(status, 1, Integer::sum);
        Instant updated = item.updatedAt() != null ? item.updatedAt() : item.createdAt();
        if (updated != null && (latestUpdatedAt == null || updated.isAfter(latestUpdatedAt))) {
          latestUpdatedAt = updated;
        }
      }
      for (VolunteerApplicationStatus status : VolunteerApplicationStatus.values()) {
        counts.putIfAbsent(status, 0);
      }
      return new MineStats(total, Map.copyOf(counts), distinctEvents.size(), latestUpdatedAt);
    }
  }

  public EventStats statsByEvent(String eventId) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        return EventStats.empty();
      }
      EnumMap<VolunteerApplicationStatus, Integer> counts =
          new EnumMap<>(VolunteerApplicationStatus.class);
      int total = 0;
      Instant latestUpdatedAt = null;
      for (VolunteerApplication item : applications.values()) {
        if (!normalizedEventId.equals(item.eventId())) {
          continue;
        }
        total++;
        VolunteerApplicationStatus status =
            item.status() != null ? item.status() : VolunteerApplicationStatus.APPLIED;
        counts.merge(status, 1, Integer::sum);
        Instant updated = item.updatedAt() != null ? item.updatedAt() : item.createdAt();
        if (updated != null && (latestUpdatedAt == null || updated.isAfter(latestUpdatedAt))) {
          latestUpdatedAt = updated;
        }
      }
      for (VolunteerApplicationStatus status : VolunteerApplicationStatus.values()) {
        counts.putIfAbsent(status, 0);
      }
      return new EventStats(total, Map.copyOf(counts), latestUpdatedAt);
    }
  }

  public void reloadFromDisk() {
    synchronized (lock) {
      refreshFromDisk(true);
    }
  }

  public void clearAllForTests() {
    synchronized (lock) {
      applications.clear();
      if (volunteerEventConfigService != null) {
        volunteerEventConfigService.resetForTests();
      }
      persistSync();
    }
  }

  public static Double calculateWeightedScore(VolunteerApplication item) {
    if (item == null) {
      return null;
    }
    return calculateWeightedScore(item.ratingProfile(), item.ratingMotivation(), item.ratingDifferentiator());
  }

  public static Double calculateWeightedScore(
      Integer profile, Integer motivation, Integer differentiator) {
    if (profile == null || motivation == null || differentiator == null) {
      return null;
    }
    double score = (profile * 0.35d) + (motivation * 0.35d) + (differentiator * 0.30d);
    return Math.round(score * 100.0d) / 100.0d;
  }

  private VolunteerApplication updateStatusInternal(
      VolunteerApplication current,
      VolunteerApplicationStatus newStatus,
      String moderator,
      String note) {
    boolean statusChanged = current.status() != newStatus;
    boolean moderatorChanged = !Objects.equals(moderator, current.moderatedBy());
    boolean noteChanged = !Objects.equals(note, current.moderationNote());
    if (!statusChanged && !moderatorChanged && !noteChanged) {
      return current;
    }
    Instant now = nextUpdatedAt(current);
    Instant moderatedAt = current.moderatedAt();
    if (moderatedAt == null || statusChanged || moderatorChanged || noteChanged) {
      moderatedAt = now;
    }
    VolunteerApplication updated =
        new VolunteerApplication(
            current.id(),
            current.eventId(),
            current.applicantUserId(),
            current.applicantName(),
            current.aboutMe(),
            current.joinReason(),
            current.differentiator(),
            newStatus,
            current.createdAt(),
            now,
            moderatedAt,
            moderator,
            note,
            current.ratingProfile(),
            current.ratingMotivation(),
            current.ratingDifferentiator());
    applications.put(updated.id(), updated);
    persistSync();
    return updated;
  }

  private VolunteerEventConfigService.ResolvedEventConfig resolveEventConfig(String eventId) {
    if (volunteerEventConfigService != null) {
      return volunteerEventConfigService.resolveForEvent(eventId);
    }
    return new VolunteerEventConfigService.ResolvedEventConfig(
        eventId,
        false,
        true,
        null,
        null,
        true);
  }

  private static void validateExpectedUpdatedAt(
      VolunteerApplication current, Instant expectedUpdatedAt) {
    if (expectedUpdatedAt == null || current == null) {
      return;
    }
    Instant currentUpdatedAt = current.updatedAt();
    if (!Objects.equals(currentUpdatedAt, expectedUpdatedAt)) {
      throw new ValidationException("stale_submission");
    }
  }

  private static Instant nextUpdatedAt(VolunteerApplication current) {
    Instant now = Instant.now();
    if (current == null || current.updatedAt() == null) {
      return now;
    }
    Instant currentUpdatedAt = current.updatedAt();
    if (!now.isAfter(currentUpdatedAt)) {
      return currentUpdatedAt.plusNanos(1);
    }
    return now;
  }

  private VolunteerApplication findOrThrow(String id) {
    String normalized = sanitizeId(id);
    if (normalized == null) {
      throw new NotFoundException("application_not_found");
    }
    VolunteerApplication found = applications.get(normalized);
    if (found == null) {
      throw new NotFoundException("application_not_found");
    }
    return found;
  }

  private void persistSync() {
    try {
      persistenceService.saveVolunteerApplicationsSync(new LinkedHashMap<>(applications));
      lastKnownMtime = persistenceService.volunteerApplicationsLastModifiedMillis();
    } catch (IllegalStateException e) {
      throw new IllegalStateException("failed_to_persist_volunteer_submission_state", e);
    }
  }

  private void refreshFromDisk(boolean force) {
    long mtime = persistenceService.volunteerApplicationsLastModifiedMillis();
    if (!force && mtime == lastKnownMtime) {
      return;
    }
    applications.clear();
    applications.putAll(persistenceService.loadVolunteerApplications());
    lastKnownMtime = mtime;
  }

  private static boolean isTransitionAllowed(
      VolunteerApplicationStatus current, VolunteerApplicationStatus target) {
    if (current == target) {
      return true;
    }
    return switch (current) {
      case APPLIED ->
          target == VolunteerApplicationStatus.UNDER_REVIEW
              || target == VolunteerApplicationStatus.SELECTED
              || target == VolunteerApplicationStatus.NOT_SELECTED
              || target == VolunteerApplicationStatus.WITHDRAWN;
      case UNDER_REVIEW ->
          target == VolunteerApplicationStatus.SELECTED
              || target == VolunteerApplicationStatus.NOT_SELECTED
              || target == VolunteerApplicationStatus.WITHDRAWN;
      case SELECTED -> target == VolunteerApplicationStatus.UNDER_REVIEW;
      case NOT_SELECTED -> target == VolunteerApplicationStatus.UNDER_REVIEW;
      case WITHDRAWN -> target == VolunteerApplicationStatus.UNDER_REVIEW;
    };
  }

  private static Comparator<VolunteerApplication> sortComparator(SortOrder sortOrder) {
    Comparator<Instant> createdComparator = Comparator.nullsLast(Comparator.reverseOrder());
    Comparator<VolunteerApplication> byCreated =
        Comparator.comparing(VolunteerApplication::createdAt, createdComparator);
    Comparator<Instant> updatedComparator = Comparator.nullsLast(Comparator.reverseOrder());
    Comparator<VolunteerApplication> byUpdated =
        Comparator.comparing(VolunteerApplication::updatedAt, updatedComparator);
    if (sortOrder == SortOrder.SCORE_DESC) {
      return Comparator.comparingDouble(VolunteerApplicationService::scoreForOrdering)
          .reversed()
          .thenComparing(byCreated);
    }
    if (sortOrder == SortOrder.UPDATED_DESC) {
      return byUpdated.thenComparing(byCreated);
    }
    return byCreated;
  }

  private static double scoreForOrdering(VolunteerApplication item) {
    Double score = calculateWeightedScore(item);
    return score != null ? score : -1d;
  }

  private static int normalizeRating(Integer value, String errorCode) {
    if (value == null || value < MIN_RATING || value > MAX_RATING) {
      throw new ValidationException(errorCode);
    }
    return value;
  }

  private static String sanitizeId(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    return value.isBlank() ? null : value;
  }

  private static String sanitizeUserId(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim().toLowerCase(Locale.ROOT);
    return value.isBlank() ? null : value;
  }

  private static String sanitizeText(String raw, int maxLength) {
    if (raw == null) {
      return null;
    }
    String cleaned = CONTROL.matcher(raw).replaceAll("").trim();
    if (cleaned.isEmpty()) {
      return null;
    }
    if (cleaned.length() > maxLength) {
      cleaned = cleaned.substring(0, maxLength).trim();
    }
    return cleaned.isEmpty() ? null : cleaned;
  }

  private static List<VolunteerApplication> paginate(
      List<VolunteerApplication> source, int requestedLimit, int requestedOffset) {
    int limit =
        PaginationGuardrails.clampLimit(
            requestedLimit,
            PaginationGuardrails.DEFAULT_PAGE_LIMIT,
            PaginationGuardrails.MAX_PAGE_LIMIT);
    int offset = PaginationGuardrails.clampOffset(requestedOffset, PaginationGuardrails.MAX_OFFSET);
    if (offset >= source.size()) {
      return List.of();
    }
    int end = Math.min(source.size(), offset + limit);
    return source.subList(offset, end);
  }

  private static Set<String> normalizeUserIds(Set<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Set.of();
    }
    return userIds.stream()
        .map(VolunteerApplicationService::sanitizeUserId)
        .filter(item -> item != null)
        .collect(java.util.stream.Collectors.toSet());
  }

  public record CreateRequest(
      String eventId, String aboutMe, String joinReason, String differentiator) {}

  public record UpdateRequest(String aboutMe, String joinReason, String differentiator) {}

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public static class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
      super(message);
    }
  }

  public static class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(String message) {
      super(message);
    }
  }

  public record EventStats(
      int total,
      Map<VolunteerApplicationStatus, Integer> countsByStatus,
      Instant latestUpdatedAt) {
    public static EventStats empty() {
      EnumMap<VolunteerApplicationStatus, Integer> empty =
          new EnumMap<>(VolunteerApplicationStatus.class);
      for (VolunteerApplicationStatus status : VolunteerApplicationStatus.values()) {
        empty.put(status, 0);
      }
      return new EventStats(0, Map.copyOf(empty), null);
    }
  }

  public record MineStats(
      int total,
      Map<VolunteerApplicationStatus, Integer> countsByStatus,
      int distinctEvents,
      Instant latestUpdatedAt) {
    public static MineStats empty() {
      EnumMap<VolunteerApplicationStatus, Integer> empty =
          new EnumMap<>(VolunteerApplicationStatus.class);
      for (VolunteerApplicationStatus status : VolunteerApplicationStatus.values()) {
        empty.put(status, 0);
      }
      return new MineStats(0, Map.copyOf(empty), 0, null);
    }
  }
}