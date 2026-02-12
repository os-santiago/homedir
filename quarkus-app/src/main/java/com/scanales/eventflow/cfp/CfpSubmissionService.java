package com.scanales.eventflow.cfp;

import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.PersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@ApplicationScoped
public class CfpSubmissionService {
  private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
  public static final int MIN_SUBMISSIONS_PER_USER_PER_EVENT = 1;
  public static final int MAX_SUBMISSIONS_PER_USER_PER_EVENT = 10;
  private static final int DEFAULT_MAX_SUBMISSIONS_PER_USER_PER_EVENT = 2;

  @Inject PersistenceService persistenceService;
  @Inject EventService eventService;
  @Inject CfpFormOptionsService cfpFormOptionsService;

  @ConfigProperty(name = "cfp.submissions.max-per-user-per-event", defaultValue = "2")
  int configuredMaxSubmissionsPerUserPerEvent;

  private volatile int runtimeMaxSubmissionsPerUserPerEvent = DEFAULT_MAX_SUBMISSIONS_PER_USER_PER_EVENT;
  private final ConcurrentHashMap<String, CfpSubmission> submissions = new ConcurrentHashMap<>();
  private final Object submissionsLock = new Object();
  private volatile long lastKnownMtime = Long.MIN_VALUE;

  @PostConstruct
  void init() {
    runtimeMaxSubmissionsPerUserPerEvent = normalizeMaxSubmissionsPerUser(configuredMaxSubmissionsPerUserPerEvent);
    synchronized (submissionsLock) {
      refreshFromDisk(true);
    }
  }

  public CfpSubmission create(String userId, String userName, CreateRequest request) {
    synchronized (submissionsLock) {
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
      String proposerId = sanitizeUserId(userId);
      if (proposerId == null) {
        throw new ValidationException("user_id_required");
      }

      String title = sanitizeText(request.title(), 160);
      String summary = sanitizeText(request.summary(), 360);
      String abstractText = sanitizeText(request.abstractText(), 5000);
      if (title == null) {
        throw new ValidationException("invalid_title");
      }
      if (abstractText == null) {
        throw new ValidationException("invalid_abstract");
      }
      if (summary == null) {
        summary = summarize(abstractText, 220);
      }
      String normalizedTitle = normalizeTitleForComparison(title);

      String level =
          cfpFormOptionsService
              .normalizeLevel(request.level())
              .orElseThrow(() -> new ValidationException("invalid_level"));
      String format =
          cfpFormOptionsService
              .normalizeFormat(request.format())
              .orElseThrow(() -> new ValidationException("invalid_format"));
      String language =
          cfpFormOptionsService
              .normalizeLanguage(request.language())
              .orElseThrow(() -> new ValidationException("invalid_language"));
      Integer durationMin;
      Optional<Integer> expectedDuration = cfpFormOptionsService.expectedDurationForFormat(format);
      if (expectedDuration.isPresent()) {
        Integer selectedDuration = request.durationMin();
        if (selectedDuration != null && !expectedDuration.get().equals(selectedDuration)) {
          throw new ValidationException("invalid_duration");
        }
        durationMin = expectedDuration.get();
      } else {
        durationMin =
            cfpFormOptionsService
                .normalizeDuration(request.durationMin())
                .orElseThrow(() -> new ValidationException("invalid_duration"));
      }
      String track =
          cfpFormOptionsService
              .normalizeTrack(request.track())
              .orElseThrow(() -> new ValidationException("invalid_track"));

      List<String> tags = sanitizeTags(request.tags(), 10, 30);
      if (!tags.contains(track)) {
        List<String> normalizedTags = new ArrayList<>();
        normalizedTags.add(track);
        normalizedTags.addAll(tags.stream().filter(item -> !item.equals(track)).toList());
        tags = normalizedTags;
      }

      validateUserProposalConstraints(eventId, proposerId, normalizedTitle);
      List<String> links = sanitizeLinks(request.links(), 5);
      Instant now = Instant.now();

      CfpSubmission submission =
          new CfpSubmission(
              UUID.randomUUID().toString(),
              eventId,
              proposerId,
              sanitizeText(userName, 120),
              title,
              summary,
              abstractText,
              level,
              format,
              durationMin,
              language,
              track,
              tags,
              links,
              CfpSubmissionStatus.PENDING,
              now,
              now,
              null,
              null,
              null);
      submissions.put(submission.id(), submission);
      persistSync();
      return submission;
    }
  }

  public List<CfpSubmission> listByEvent(
      String eventId,
      Optional<CfpSubmissionStatus> statusFilter,
      int requestedLimit,
      int requestedOffset) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        return List.of();
      }
      Comparator<Instant> createdComparator = Comparator.nullsLast(Comparator.reverseOrder());
      List<CfpSubmission> filtered =
          submissions.values().stream()
              .filter(item -> normalizedEventId.equals(item.eventId()))
              .filter(item -> statusFilter.isEmpty() || item.status() == statusFilter.get())
              .sorted(Comparator.comparing(CfpSubmission::createdAt, createdComparator))
              .toList();
      return paginate(filtered, requestedLimit, requestedOffset);
    }
  }

  public List<CfpSubmission> listMine(String eventId, String userId, int requestedLimit, int requestedOffset) {
    if (userId == null) {
      return List.of();
    }
    return listMine(eventId, Set.of(userId), requestedLimit, requestedOffset);
  }

  public List<CfpSubmission> listMine(
      String eventId, Set<String> userIds, int requestedLimit, int requestedOffset) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null || userIds == null || userIds.isEmpty()) {
        return List.of();
      }
      Set<String> normalizedUserIds =
          userIds.stream()
              .map(CfpSubmissionService::sanitizeUserId)
              .filter(item -> item != null)
              .collect(java.util.stream.Collectors.toSet());
      if (normalizedUserIds.isEmpty()) {
        return List.of();
      }
      Comparator<Instant> createdComparator = Comparator.nullsLast(Comparator.reverseOrder());
      List<CfpSubmission> filtered =
          submissions.values().stream()
              .filter(item -> normalizedEventId.equals(item.eventId()))
              .filter(item -> normalizedUserIds.contains(item.proposerUserId()))
              .sorted(Comparator.comparing(CfpSubmission::createdAt, createdComparator))
              .toList();
      return paginate(filtered, requestedLimit, requestedOffset);
    }
  }

  public Optional<CfpSubmission> findById(String id) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalized = sanitizeId(id);
      if (normalized == null) {
        return Optional.empty();
      }
      return Optional.ofNullable(submissions.get(normalized));
    }
  }

  public CfpSubmission updateStatus(String id, CfpSubmissionStatus newStatus, String moderator, String note) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      if (newStatus == null) {
        throw new ValidationException("status_required");
      }
      CfpSubmission current = findOrThrow(id);
      if (!isTransitionAllowed(current.status(), newStatus)) {
        throw new InvalidTransitionException("invalid_status_transition");
      }
      if (current.status() == newStatus) {
        return current;
      }
      Instant now = Instant.now();
      CfpSubmission updated =
          new CfpSubmission(
              current.id(),
              current.eventId(),
              current.proposerUserId(),
              current.proposerName(),
              current.title(),
              current.summary(),
              current.abstractText(),
              current.level(),
              current.format(),
              current.durationMin(),
              current.language(),
              current.track(),
              current.tags(),
              current.links(),
              newStatus,
              current.createdAt(),
              now,
              now,
              sanitizeText(moderator, 200),
              sanitizeText(note, 500));
      submissions.put(updated.id(), updated);
      persistSync();
      return updated;
    }
  }

  public CfpSubmission delete(String eventId, String id) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      String normalizedId = sanitizeId(id);
      if (normalizedEventId == null || normalizedId == null) {
        throw new NotFoundException("submission_not_found");
      }
      CfpSubmission existing = submissions.get(normalizedId);
      if (existing == null || !normalizedEventId.equals(existing.eventId())) {
        throw new NotFoundException("submission_not_found");
      }
      submissions.remove(normalizedId);
      persistSync();
      return existing;
    }
  }

  public void clearAllForTests() {
    synchronized (submissionsLock) {
      submissions.clear();
      runtimeMaxSubmissionsPerUserPerEvent =
          normalizeMaxSubmissionsPerUser(configuredMaxSubmissionsPerUserPerEvent);
      persistSync();
    }
  }

  public void reloadFromDisk() {
    synchronized (submissionsLock) {
      refreshFromDisk(true);
    }
  }

  public int currentMaxSubmissionsPerUserPerEvent() {
    return runtimeMaxSubmissionsPerUserPerEvent;
  }

  public int updateMaxSubmissionsPerUserPerEvent(int requestedLimit) {
    synchronized (submissionsLock) {
      runtimeMaxSubmissionsPerUserPerEvent = normalizeMaxSubmissionsPerUser(requestedLimit);
      return runtimeMaxSubmissionsPerUserPerEvent;
    }
  }

  private CfpSubmission findOrThrow(String id) {
    String normalized = sanitizeId(id);
    if (normalized == null) {
      throw new NotFoundException("submission_not_found");
    }
    CfpSubmission submission = submissions.get(normalized);
    if (submission == null) {
      throw new NotFoundException("submission_not_found");
    }
    return submission;
  }

  private void persistSync() {
    try {
      persistenceService.saveCfpSubmissionsSync(new LinkedHashMap<>(submissions));
      lastKnownMtime = persistenceService.cfpSubmissionsLastModifiedMillis();
    } catch (IllegalStateException e) {
      throw new IllegalStateException("failed_to_persist_cfp_submission_state", e);
    }
  }

  private void refreshFromDisk(boolean force) {
    long mtime = persistenceService.cfpSubmissionsLastModifiedMillis();
    if (!force && mtime == lastKnownMtime) {
      return;
    }
    submissions.clear();
    submissions.putAll(persistenceService.loadCfpSubmissions());
    lastKnownMtime = mtime;
  }

  private static boolean isTransitionAllowed(CfpSubmissionStatus current, CfpSubmissionStatus target) {
    if (current == target) {
      return true;
    }
    return switch (current) {
      case PENDING ->
        target == CfpSubmissionStatus.UNDER_REVIEW
            || target == CfpSubmissionStatus.ACCEPTED
            || target == CfpSubmissionStatus.REJECTED
            || target == CfpSubmissionStatus.WITHDRAWN;
      case UNDER_REVIEW ->
        target == CfpSubmissionStatus.ACCEPTED
            || target == CfpSubmissionStatus.REJECTED
            || target == CfpSubmissionStatus.WITHDRAWN;
      case ACCEPTED -> target == CfpSubmissionStatus.UNDER_REVIEW;
      case REJECTED -> target == CfpSubmissionStatus.UNDER_REVIEW;
      case WITHDRAWN -> target == CfpSubmissionStatus.UNDER_REVIEW;
    };
  }

  private void validateUserProposalConstraints(
      String eventId, String proposerId, String normalizedTitle) {
    int existingCount = 0;
    for (CfpSubmission item : submissions.values()) {
      if (!eventId.equals(item.eventId())) {
        continue;
      }
      if (!proposerId.equals(item.proposerUserId())) {
        continue;
      }
      existingCount++;
      if (normalizedTitle != null) {
        String existingTitle = normalizeTitleForComparison(item.title());
        if (existingTitle != null && existingTitle.equals(normalizedTitle)) {
          throw new ValidationException("duplicate_title");
        }
      }
    }
    if (existingCount >= maxSubmissionsPerUserPerEvent()) {
      throw new ValidationException("proposal_limit_reached");
    }
  }

  private int maxSubmissionsPerUserPerEvent() {
    return runtimeMaxSubmissionsPerUserPerEvent;
  }

  private static int normalizeMaxSubmissionsPerUser(int rawValue) {
    if (rawValue <= 0) {
      return DEFAULT_MAX_SUBMISSIONS_PER_USER_PER_EVENT;
    }
    if (rawValue < MIN_SUBMISSIONS_PER_USER_PER_EVENT) {
      return MIN_SUBMISSIONS_PER_USER_PER_EVENT;
    }
    return Math.min(rawValue, MAX_SUBMISSIONS_PER_USER_PER_EVENT);
  }

  private static String normalizeTitleForComparison(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    return value.isBlank() ? null : value;
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

  private static List<String> sanitizeTags(List<String> rawTags, int maxTags, int maxLength) {
    if (rawTags == null || rawTags.isEmpty() || maxTags <= 0) {
      return List.of();
    }
    Set<String> unique = new LinkedHashSet<>();
    for (String tag : rawTags) {
      String normalized = sanitizeText(tag, maxLength);
      if (normalized != null) {
        unique.add(normalized.toLowerCase(Locale.ROOT));
      }
      if (unique.size() >= maxTags) {
        break;
      }
    }
    return unique.isEmpty() ? List.of() : new ArrayList<>(unique);
  }

  private static List<String> sanitizeLinks(List<String> rawLinks, int maxLinks) {
    if (rawLinks == null || rawLinks.isEmpty() || maxLinks <= 0) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    Set<String> unique = new LinkedHashSet<>();
    for (String link : rawLinks) {
      String normalized = sanitizeUrl(link);
      if (normalized == null) {
        continue;
      }
      if (unique.add(normalized)) {
        result.add(normalized);
      }
      if (result.size() >= maxLinks) {
        break;
      }
    }
    return result.isEmpty() ? List.of() : result;
  }

  private static String sanitizeUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(raw.trim()).normalize();
      String scheme = uri.getScheme();
      if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
        return null;
      }
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        return null;
      }
      return uri.toString();
    } catch (Exception e) {
      return null;
    }
  }

  private static String summarize(String source, int maxLength) {
    if (source == null || source.isBlank()) {
      return null;
    }
    String summary = source.trim();
    int newline = summary.indexOf('\n');
    if (newline >= 0) {
      summary = summary.substring(0, newline);
    }
    if (summary.length() > maxLength) {
      summary = summary.substring(0, maxLength).trim();
    }
    return summary;
  }

  private static List<CfpSubmission> paginate(
      List<CfpSubmission> source, int requestedLimit, int requestedOffset) {
    int limit = requestedLimit <= 0 ? 20 : Math.min(requestedLimit, 100);
    int offset = Math.max(0, requestedOffset);
    if (offset >= source.size()) {
      return List.of();
    }
    int end = Math.min(source.size(), offset + limit);
    return source.subList(offset, end);
  }

  public record CreateRequest(
      String eventId,
      String title,
      String summary,
      String abstractText,
      String level,
      String format,
      Integer durationMin,
      String language,
      String track,
      List<String> tags,
      List<String> links) {
  }

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
}

