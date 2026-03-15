package com.scanales.eventflow.cfp;

import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.PersistenceService;
import com.scanales.eventflow.service.UserProfileService;
import com.scanales.eventflow.util.PaginationGuardrails;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
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
  private static final int MIN_RATING = 0;
  private static final int MAX_RATING = 5;
  private static final double WEIGHT_TECHNICAL_DETAIL = 0.4d;
  private static final double WEIGHT_NARRATIVE = 0.3d;
  private static final double WEIGHT_CONTENT_IMPACT = 0.3d;

  public enum SortOrder {
    CREATED_DESC,
    UPDATED_DESC,
    SCORE_DESC
  }

  public record ModerationFilter(String proposedBy, String title, String track) {
    public static ModerationFilter empty() {
      return new ModerationFilter(null, null, null);
    }
  }

  @Inject PersistenceService persistenceService;
  @Inject EventService eventService;
  @Inject CfpFormOptionsService cfpFormOptionsService;
  @Inject CfpConfigService cfpConfigService;
  @Inject CfpEventConfigService cfpEventConfigService;
  @Inject UserProfileService userProfileService;

  private final ConcurrentHashMap<String, CfpSubmission> submissions = new ConcurrentHashMap<>();
  private final Object submissionsLock = new Object();
  private volatile long lastKnownMtime = Long.MIN_VALUE;

  @PostConstruct
  void init() {
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
      CfpEventConfigService.ResolvedEventConfig eventConfig = resolveEventConfig(eventId);
      if (!eventConfig.currentlyOpen()) {
        throw new ValidationException("submissions_closed");
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

      validateUserProposalConstraints(
          eventId, proposerId, normalizedTitle, eventConfig.maxSubmissionsPerUserPerEvent());
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
              CfpSubmissionStatus.UNDER_REVIEW,
              now,
              now,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(),
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
    return listByEvent(
        eventId,
        statusFilter,
        ModerationFilter.empty(),
        SortOrder.CREATED_DESC,
        requestedLimit,
        requestedOffset);
  }

  public List<CfpSubmission> listByEvent(
      String eventId,
      Optional<CfpSubmissionStatus> statusFilter,
      SortOrder sortOrder,
      int requestedLimit,
      int requestedOffset) {
    return listByEvent(
        eventId, statusFilter, ModerationFilter.empty(), sortOrder, requestedLimit, requestedOffset);
  }

  public List<CfpSubmission> listByEvent(
      String eventId,
      Optional<CfpSubmissionStatus> statusFilter,
      ModerationFilter moderationFilter,
      SortOrder sortOrder,
      int requestedLimit,
      int requestedOffset) {
    return paginate(
        listByEventAll(eventId, statusFilter, moderationFilter, sortOrder),
        requestedLimit,
        requestedOffset);
  }

  public List<CfpSubmission> listByEventAll(
      String eventId, Optional<CfpSubmissionStatus> statusFilter, SortOrder sortOrder) {
    return listByEventAll(eventId, statusFilter, ModerationFilter.empty(), sortOrder);
  }

  public List<CfpSubmission> listByEventAll(
      String eventId,
      Optional<CfpSubmissionStatus> statusFilter,
      ModerationFilter moderationFilter,
      SortOrder sortOrder) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        return List.of();
      }
      return submissions.values().stream()
          .filter(item -> normalizedEventId.equals(item.eventId()))
          .filter(item -> statusFilter.isEmpty() || item.status() == statusFilter.get())
          .filter(item -> matchesModerationFilter(item, moderationFilter))
          .sorted(sortComparator(sortOrder))
          .toList();
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
      Set<String> normalizedUserIds = normalizeUserIds(userIds);
      if (normalizedUserIds.isEmpty()) {
        return List.of();
      }
      Comparator<Instant> createdComparator = Comparator.nullsLast(Comparator.reverseOrder());
      List<CfpSubmission> filtered =
          submissions.values().stream()
              .filter(item -> normalizedEventId.equals(item.eventId()))
              .filter(item -> matchesMineIdentity(item, normalizedUserIds))
              .sorted(Comparator.comparing(CfpSubmission::createdAt, createdComparator))
              .toList();
      return paginate(filtered, requestedLimit, requestedOffset);
    }
  }

  public int countByEvent(String eventId, Optional<CfpSubmissionStatus> statusFilter) {
    return countByEvent(eventId, statusFilter, ModerationFilter.empty());
  }

  public int countByEvent(
      String eventId,
      Optional<CfpSubmissionStatus> statusFilter,
      ModerationFilter moderationFilter) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        return 0;
      }
      int count = 0;
      for (CfpSubmission item : submissions.values()) {
        if (!normalizedEventId.equals(item.eventId())) {
          continue;
        }
        if (statusFilter.isPresent() && item.status() != statusFilter.get()) {
          continue;
        }
        if (!matchesModerationFilter(item, moderationFilter)) {
          continue;
        }
        count++;
      }
      return count;
    }
  }

  public int countMine(String eventId, Set<String> userIds) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null || userIds == null || userIds.isEmpty()) {
        return 0;
      }
      Set<String> normalizedUserIds = normalizeUserIds(userIds);
      if (normalizedUserIds.isEmpty()) {
        return 0;
      }
      int count = 0;
      for (CfpSubmission item : submissions.values()) {
        if (!normalizedEventId.equals(item.eventId())) {
          continue;
        }
        if (matchesMineIdentity(item, normalizedUserIds)) {
          count++;
        }
      }
      return count;
    }
  }

  public int countMineOwned(String eventId, Set<String> userIds) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null || userIds == null || userIds.isEmpty()) {
        return 0;
      }
      Set<String> normalizedUserIds = normalizeUserIds(userIds);
      if (normalizedUserIds.isEmpty()) {
        return 0;
      }
      int count = 0;
      for (CfpSubmission item : submissions.values()) {
        if (!normalizedEventId.equals(item.eventId())) {
          continue;
        }
        String proposer = sanitizeUserId(item.proposerUserId());
        if (proposer != null && normalizedUserIds.contains(proposer)) {
          count++;
        }
      }
      return count;
    }
  }

  public List<CfpSubmission> listMineAcrossEvents(
      Set<String> userIds, SortOrder sortOrder, int requestedLimit, int requestedOffset) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      if (userIds == null || userIds.isEmpty()) {
        return List.of();
      }
      Set<String> normalizedUserIds = normalizeUserIds(userIds);
      if (normalizedUserIds.isEmpty()) {
        return List.of();
      }
      List<CfpSubmission> filtered =
          submissions.values().stream()
              .filter(item -> matchesMineIdentity(item, normalizedUserIds))
              .sorted(sortComparator(sortOrder))
              .toList();
      return paginate(filtered, requestedLimit, requestedOffset);
    }
  }

  public MineStats statsMineAcrossEvents(Set<String> userIds) {
    return statsMineAcrossEvents(userIds, false);
  }

  public MineStats visibleStatsMineAcrossEvents(Set<String> userIds) {
    return statsMineAcrossEvents(userIds, true);
  }

  private MineStats statsMineAcrossEvents(Set<String> userIds, boolean visibleStatus) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      if (userIds == null || userIds.isEmpty()) {
        return MineStats.empty();
      }
      Set<String> normalizedUserIds = normalizeUserIds(userIds);
      if (normalizedUserIds.isEmpty()) {
        return MineStats.empty();
      }
      EnumMap<CfpSubmissionStatus, Integer> counts = new EnumMap<>(CfpSubmissionStatus.class);
      Set<String> distinctEvents = new LinkedHashSet<>();
      int total = 0;
      Instant latestUpdatedAt = null;
      for (CfpSubmission item : submissions.values()) {
        if (!matchesMineIdentity(item, normalizedUserIds)) {
          continue;
        }
        total++;
        if (item.eventId() != null && !item.eventId().isBlank()) {
          distinctEvents.add(item.eventId());
        }
        CfpSubmissionStatus status =
            visibleStatus
                ? visibleStatus(item)
                : (item.status() != null ? item.status() : CfpSubmissionStatus.PENDING);
        counts.merge(status, 1, Integer::sum);
        Instant updated = item.updatedAt() != null ? item.updatedAt() : item.createdAt();
        if (updated != null && (latestUpdatedAt == null || updated.isAfter(latestUpdatedAt))) {
          latestUpdatedAt = updated;
        }
      }
      for (CfpSubmissionStatus status : CfpSubmissionStatus.values()) {
        counts.putIfAbsent(status, 0);
      }
      return new MineStats(total, Map.copyOf(counts), distinctEvents.size(), latestUpdatedAt);
    }
  }

  public EventStats statsByEvent(String eventId) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        return EventStats.empty();
      }
      EnumMap<CfpSubmissionStatus, Integer> counts = new EnumMap<>(CfpSubmissionStatus.class);
      int total = 0;
      Instant latestUpdatedAt = null;
      for (CfpSubmission item : submissions.values()) {
        if (!normalizedEventId.equals(item.eventId())) {
          continue;
        }
        total++;
        CfpSubmissionStatus status = item.status() != null ? item.status() : CfpSubmissionStatus.PENDING;
        counts.merge(status, 1, Integer::sum);
        Instant updated = item.updatedAt();
        if (updated != null && (latestUpdatedAt == null || updated.isAfter(latestUpdatedAt))) {
          latestUpdatedAt = updated;
        }
      }
      for (CfpSubmissionStatus status : CfpSubmissionStatus.values()) {
        counts.putIfAbsent(status, 0);
      }
      return new EventStats(total, Map.copyOf(counts), latestUpdatedAt);
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
    return updateStatus(id, newStatus, moderator, note, null);
  }

  public CfpSubmission updateStatus(
      String id, CfpSubmissionStatus newStatus, String moderator, String note, Instant expectedUpdatedAt) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      if (newStatus == null) {
        throw new ValidationException("status_required");
      }
      CfpSubmission current = findOrThrow(id);
      validateExpectedUpdatedAt(current, expectedUpdatedAt);
      if (!isTransitionAllowed(current.status(), newStatus)) {
        throw new InvalidTransitionException("invalid_status_transition");
      }

      // Allow updating moderation metadata (note/by) even when the status stays the same.
      // This avoids "notes don't persist" UX when an admin wants to add/edit a note
      // after setting the status, or without changing it.
      String normalizedModerator = sanitizeText(moderator, 200);
      String normalizedNote = sanitizeText(note, 500);
      // Treat empty/blank note as "no change" to avoid accidental wipeouts.
      if (normalizedNote == null) {
        normalizedNote = current.moderationNote();
      }
      if (newStatus == CfpSubmissionStatus.REJECTED && (normalizedNote == null || normalizedNote.isBlank())) {
        throw new ValidationException("reject_note_required");
      }

      boolean statusChanged = current.status() != newStatus;
      boolean moderatorChanged = !Objects.equals(normalizedModerator, current.moderatedBy());
      boolean noteChanged = !Objects.equals(normalizedNote, current.moderationNote());
      if (!statusChanged && !moderatorChanged && !noteChanged) {
        return current;
      }

      Instant now = nextUpdatedAt(current);
      Instant moderatedAt = current.moderatedAt();
      if (moderatedAt == null || statusChanged || moderatorChanged || noteChanged) {
        moderatedAt = now;
      }
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
              moderatedAt,
              normalizedModerator,
              normalizedNote,
              current.ratingTechnicalDetail(),
              current.ratingNarrative(),
              current.ratingContentImpact(),
              current.panelists() == null ? List.of() : current.panelists(),
              current.presentationAsset());
      submissions.put(updated.id(), updated);
      persistSync();
      return updated;
    }
  }

  public CfpSubmission updateRating(
      String eventId,
      String id,
      Integer technicalDetail,
      Integer narrative,
      Integer contentImpact,
      String moderator) {
    return updateRating(eventId, id, technicalDetail, narrative, contentImpact, moderator, null);
  }

  public CfpSubmission updateRating(
      String eventId,
      String id,
      Integer technicalDetail,
      Integer narrative,
      Integer contentImpact,
      String moderator,
      Instant expectedUpdatedAt) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        throw new NotFoundException("submission_not_found");
      }
      CfpSubmission current = findOrThrow(id);
      if (!normalizedEventId.equals(current.eventId())) {
        throw new NotFoundException("submission_not_found");
      }
      validateExpectedUpdatedAt(current, expectedUpdatedAt);

      int normalizedTechnical = normalizeRating(technicalDetail, "invalid_rating_technical_detail");
      int normalizedNarrative = normalizeRating(narrative, "invalid_rating_narrative");
      int normalizedImpact = normalizeRating(contentImpact, "invalid_rating_content_impact");

      Instant now = nextUpdatedAt(current);
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
              current.status(),
              current.createdAt(),
              now,
              current.moderatedAt(),
              sanitizeText(moderator, 200),
              current.moderationNote(),
              normalizedTechnical,
              normalizedNarrative,
              normalizedImpact,
              current.panelists() == null ? List.of() : current.panelists(),
              current.presentationAsset());
      submissions.put(updated.id(), updated);
      persistSync();
      return updated;
    }
  }

  public CfpSubmission updatePanelists(
      String eventId,
      String id,
      List<PanelistInput> panelists,
      String actorUserId,
      Instant expectedUpdatedAt) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        throw new NotFoundException("submission_not_found");
      }
      CfpSubmission current = findOrThrow(id);
      if (!normalizedEventId.equals(current.eventId())) {
        throw new NotFoundException("submission_not_found");
      }
      validateExpectedUpdatedAt(current, expectedUpdatedAt);
      List<CfpPanelist> normalized = normalizePanelists(current, panelists);
      Instant now = nextUpdatedAt(current);
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
              current.status(),
              current.createdAt(),
              now,
              current.moderatedAt(),
              current.moderatedBy(),
              current.moderationNote(),
              current.ratingTechnicalDetail(),
              current.ratingNarrative(),
              current.ratingContentImpact(),
              normalized,
              current.presentationAsset());
      submissions.put(updated.id(), updated);
      persistSync();
      return updated;
    }
  }

  public CfpSubmission updatePresentationAsset(
      String eventId,
      String id,
      CfpPresentationAsset presentationAsset,
      String actorUserId,
      Instant expectedUpdatedAt) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        throw new NotFoundException("submission_not_found");
      }
      CfpSubmission current = findOrThrow(id);
      if (!normalizedEventId.equals(current.eventId())) {
        throw new NotFoundException("submission_not_found");
      }
      validateExpectedUpdatedAt(current, expectedUpdatedAt);
      if (!isPresentationUploadAllowed(current)) {
        throw new ValidationException("presentation_requires_accepted_submission");
      }
      CfpPresentationAsset sanitized = sanitizePresentationAsset(presentationAsset, actorUserId);
      if ("panel".equalsIgnoreCase(current.format())) {
        if (current.panelists() == null || current.panelists().isEmpty()) {
          throw new ValidationException("panel_requires_panelists");
        }
        String normalizedActor = sanitizeUserId(actorUserId);
        boolean actorAllowed =
            normalizedActor != null
                && (normalizedActor.equals(sanitizeUserId(current.proposerUserId()))
                    || current.panelists().stream()
                        .map(CfpPanelist::userId)
                        .map(CfpSubmissionService::sanitizeUserId)
                        .anyMatch(normalizedActor::equals));
        if (!actorAllowed) {
          throw new ValidationException("panel_uploader_not_allowed");
        }
      }
      Instant now = nextUpdatedAt(current);
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
              current.status(),
              current.createdAt(),
              now,
              current.moderatedAt(),
              current.moderatedBy(),
              current.moderationNote(),
              current.ratingTechnicalDetail(),
              current.ratingNarrative(),
              current.ratingContentImpact(),
              current.panelists() == null ? List.of() : current.panelists(),
              sanitized);
      submissions.put(updated.id(), updated);
      persistSync();
      return updated;
    }
  }

  public CfpSubmission refreshPanelistsLinkState(String eventId, String id) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        throw new NotFoundException("submission_not_found");
      }
      CfpSubmission current = findOrThrow(id);
      if (!normalizedEventId.equals(current.eventId())) {
        throw new NotFoundException("submission_not_found");
      }
      if (current.panelists() == null || current.panelists().isEmpty()) {
        return current;
      }
      List<PanelistInput> inputs =
          current.panelists().stream()
              .map(item -> new PanelistInput(item.name(), item.email(), item.userId()))
              .toList();
      List<CfpPanelist> refreshed = normalizePanelists(current, inputs);
      if (Objects.equals(refreshed, current.panelists())) {
        return current;
      }
      Instant now = nextUpdatedAt(current);
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
              current.status(),
              current.createdAt(),
              now,
              current.moderatedAt(),
              current.moderatedBy(),
              current.moderationNote(),
              current.ratingTechnicalDetail(),
              current.ratingNarrative(),
              current.ratingContentImpact(),
              refreshed,
              current.presentationAsset());
      submissions.put(updated.id(), updated);
      persistSync();
      return updated;
    }
  }

  private static void validateExpectedUpdatedAt(CfpSubmission current, Instant expectedUpdatedAt) {
    if (expectedUpdatedAt == null || current == null) {
      return;
    }
    Instant currentUpdatedAt = current.updatedAt();
    if (!Objects.equals(currentUpdatedAt, expectedUpdatedAt)) {
      throw new ValidationException("stale_submission");
    }
  }

  private List<CfpPanelist> normalizePanelists(CfpSubmission current, List<PanelistInput> requested) {
    if (requested == null || requested.isEmpty()) {
      return List.of();
    }
    if (!"panel".equalsIgnoreCase(current.format())) {
      throw new ValidationException("panelists_allowed_only_for_panel_format");
    }
    if (requested.size() > 4) {
      throw new ValidationException("panelists_max_reached");
    }
    Map<String, CfpPanelist> previousById = new HashMap<>();
    if (current.panelists() != null) {
      for (CfpPanelist existing : current.panelists()) {
        if (existing != null && existing.id() != null) {
          previousById.put(existing.id(), existing);
        }
      }
    }
    LinkedHashMap<String, CfpPanelist> normalized = new LinkedHashMap<>();
    Instant now = Instant.now();
    for (PanelistInput input : requested) {
      if (input == null) {
        continue;
      }
      String name = sanitizeText(input.name(), 120);
      String email = sanitizeEmail(input.email());
      String userId = sanitizeUserId(input.userId());
      if (email != null) {
        userId =
            userProfileService.findByEmail(email).map(profile -> sanitizeUserId(profile.getUserId())).orElse(userId);
      }
      if (name == null && userId == null && email == null) {
        continue;
      }
      String idToken = panelistToken(name, email, userId);
      CfpPanelist previous = previousById.get(idToken);
      String status =
          userId != null ? CfpPanelistStatus.LINKED.apiValue() : CfpPanelistStatus.PENDING_LOGIN.apiValue();
      CfpPanelist normalizedItem =
          new CfpPanelist(
              idToken,
              name != null ? name : (email != null ? email : userId),
              email,
              userId,
              status,
              previous != null ? previous.createdAt() : now,
              now);
      normalized.put(idToken, normalizedItem);
    }
    if (normalized.size() > 4) {
      throw new ValidationException("panelists_max_reached");
    }
    return List.copyOf(normalized.values());
  }

  private CfpPresentationAsset sanitizePresentationAsset(CfpPresentationAsset asset, String actorUserId) {
    if (asset == null) {
      throw new ValidationException("presentation_asset_required");
    }
    String fileName = sanitizeText(asset.fileName(), 180);
    String contentType = sanitizeText(asset.contentType(), 80);
    String storagePath = sanitizeText(asset.storagePath(), 500);
    long size = asset.sizeBytes();
    if (fileName == null || storagePath == null) {
      throw new ValidationException("presentation_asset_required");
    }
    if (contentType == null) {
      contentType = "application/pdf";
    }
    String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
    if (!normalizedContentType.contains("pdf")) {
      throw new ValidationException("invalid_presentation_content_type");
    }
    if (size <= 0 || size > 25L * 1024L * 1024L) {
      throw new ValidationException("invalid_presentation_size");
    }
    return new CfpPresentationAsset(
        fileName,
        "application/pdf",
        size,
        storagePath,
        sanitizeUserId(actorUserId),
        Instant.now());
  }

  private static String sanitizeEmail(String raw) {
    String value = sanitizeText(raw, 200);
    if (value == null) {
      return null;
    }
    String normalized = value.toLowerCase(Locale.ROOT);
    if (!normalized.contains("@") || normalized.startsWith("@") || normalized.endsWith("@")) {
      return null;
    }
    return normalized;
  }

  private static String panelistToken(String name, String email, String userId) {
    String base = userId != null ? userId : (email != null ? email : name);
    String normalized = sanitizeId(base);
    if (normalized == null || normalized.isBlank()) {
      normalized = "panelist-" + UUID.randomUUID();
    }
    return normalized;
  }

  private static Instant nextUpdatedAt(CfpSubmission current) {
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
      if (cfpConfigService != null) {
        cfpConfigService.resetForTests();
      }
      if (cfpEventConfigService != null) {
        cfpEventConfigService.resetForTests();
      }
      persistSync();
    }
  }

  public void reloadFromDisk() {
    synchronized (submissionsLock) {
      refreshFromDisk(true);
    }
  }

  public int currentMaxSubmissionsPerUserPerEvent() {
    return cfpConfigService != null ? cfpConfigService.currentMaxSubmissionsPerUserPerEvent() : DEFAULT_MAX_SUBMISSIONS_PER_USER_PER_EVENT;
  }

  public CfpSubmissionStatus visibleStatus(CfpSubmission submission) {
    if (submission == null) {
      return CfpSubmissionStatus.PENDING;
    }
    CfpSubmissionStatus internal = submission.status() != null ? submission.status() : CfpSubmissionStatus.PENDING;
    if (internal == CfpSubmissionStatus.ACCEPTED || internal == CfpSubmissionStatus.REJECTED) {
      CfpEventConfigService.ResolvedEventConfig eventConfig = resolveEventConfig(submission.eventId());
      if (!eventConfig.resultsPublished()) {
        return CfpSubmissionStatus.UNDER_REVIEW;
      }
    }
    return internal;
  }

  public String resultMessage(CfpSubmission submission) {
    if (submission == null) {
      return null;
    }
    CfpSubmissionStatus visibleStatus = visibleStatus(submission);
    CfpEventConfigService.ResolvedEventConfig eventConfig = resolveEventConfig(submission.eventId());
    if (!eventConfig.resultsPublished()) {
      return null;
    }
    return switch (visibleStatus) {
      case ACCEPTED -> sanitizeText(eventConfig.acceptedResultsMessage(), 1200);
      case REJECTED -> sanitizeText(eventConfig.rejectedResultsMessage(), 1200);
      default -> null;
    };
  }

  public boolean areResultsPublished(String eventId) {
    return resolveEventConfig(eventId).resultsPublished();
  }

  public Instant resultsPublishedAt(String eventId) {
    return resolveEventConfig(eventId).resultsPublishedAt();
  }

  public boolean isPresentationUploadAllowed(CfpSubmission submission) {
    return visibleStatus(submission) == CfpSubmissionStatus.ACCEPTED;
  }

  public List<CfpSubmission> acceptedVisibleSubmissionsForEvent(String eventId) {
    synchronized (submissionsLock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        return List.of();
      }
      return submissions.values().stream()
          .filter(item -> normalizedEventId.equals(item.eventId()))
          .filter(item -> visibleStatus(item) == CfpSubmissionStatus.ACCEPTED)
          .sorted(sortComparator(SortOrder.UPDATED_DESC))
          .toList();
    }
  }

  public int updateMaxSubmissionsPerUserPerEvent(int requestedLimit) {
    return cfpConfigService != null ? cfpConfigService.updateMaxSubmissionsPerUserPerEvent(requestedLimit) : DEFAULT_MAX_SUBMISSIONS_PER_USER_PER_EVENT;
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

  private static Comparator<CfpSubmission> sortComparator(SortOrder sortOrder) {
    Comparator<Instant> createdComparator = Comparator.nullsLast(Comparator.reverseOrder());
    Comparator<CfpSubmission> byCreated = Comparator.comparing(CfpSubmission::createdAt, createdComparator);
    Comparator<Instant> updatedComparator = Comparator.nullsLast(Comparator.reverseOrder());
    Comparator<CfpSubmission> byUpdated = Comparator.comparing(CfpSubmission::updatedAt, updatedComparator);
    if (sortOrder == SortOrder.SCORE_DESC) {
      return Comparator.comparingDouble(CfpSubmissionService::scoreForOrdering).reversed().thenComparing(byCreated);
    }
    if (sortOrder == SortOrder.UPDATED_DESC) {
      return byUpdated.thenComparing(byCreated);
    }
    return byCreated;
  }

  private static double scoreForOrdering(CfpSubmission submission) {
    Double score = calculateWeightedScore(submission);
    return score != null ? score : -1d;
  }

  public static Double calculateWeightedScore(CfpSubmission submission) {
    if (submission == null) {
      return null;
    }
    return calculateWeightedScore(
        submission.ratingTechnicalDetail(), submission.ratingNarrative(), submission.ratingContentImpact());
  }

  public static Double calculateWeightedScore(Integer technicalDetail, Integer narrative, Integer contentImpact) {
    if (technicalDetail == null || narrative == null || contentImpact == null) {
      return null;
    }
    double score =
        (technicalDetail * WEIGHT_TECHNICAL_DETAIL)
            + (narrative * WEIGHT_NARRATIVE)
            + (contentImpact * WEIGHT_CONTENT_IMPACT);
    return Math.round(score * 100.0d) / 100.0d;
  }

  private static int normalizeRating(Integer value, String errorCode) {
    if (value == null || value < MIN_RATING || value > MAX_RATING) {
      throw new ValidationException(errorCode);
    }
    return value;
  }
  private void validateUserProposalConstraints(
      String eventId, String proposerId, String normalizedTitle, int maxPerUserForEvent) {
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
    if (existingCount >= maxPerUserForEvent) {
      throw new ValidationException("proposal_limit_reached");
    }
  }

  private CfpEventConfigService.ResolvedEventConfig resolveEventConfig(String eventId) {
    if (cfpEventConfigService != null) {
      return cfpEventConfigService.resolveForEvent(eventId);
    }
    CfpConfig global =
        cfpConfigService != null
            ? cfpConfigService.current()
            : CfpConfig.defaults(DEFAULT_MAX_SUBMISSIONS_PER_USER_PER_EVENT, true);
      return new CfpEventConfigService.ResolvedEventConfig(
        eventId,
        false,
        true,
        null,
        null,
        global.maxSubmissionsPerUserPerEvent(),
        global.testingModeEnabled(),
        true,
        false,
        null,
        null,
        null,
        null);
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

  private static boolean matchesModerationFilter(
      CfpSubmission item, ModerationFilter moderationFilter) {
    if (item == null || moderationFilter == null) {
      return true;
    }
    String proposedBy = normalizeSearchToken(moderationFilter.proposedBy());
    if (proposedBy != null
        && !containsIgnoreCase(item.proposerName(), proposedBy)
        && !containsIgnoreCase(item.proposerUserId(), proposedBy)) {
      return false;
    }
    String title = normalizeSearchToken(moderationFilter.title());
    if (title != null && !containsIgnoreCase(item.title(), title)) {
      return false;
    }
    String track = normalizeSearchToken(moderationFilter.track());
    if (track != null && !containsIgnoreCase(item.track(), track)) {
      return false;
    }
    return true;
  }

  private static boolean containsIgnoreCase(String value, String query) {
    if (query == null) {
      return true;
    }
    if (value == null || value.isBlank()) {
      return false;
    }
    return value.toLowerCase(Locale.ROOT).contains(query);
  }

  private static String normalizeSearchToken(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return normalized.isEmpty() ? null : normalized;
  }

  private static Set<String> normalizeUserIds(Set<String> userIds) {
    return userIds.stream()
        .map(CfpSubmissionService::sanitizeUserId)
        .filter(item -> item != null)
        .collect(java.util.stream.Collectors.toSet());
  }

  private static boolean matchesMineIdentity(CfpSubmission item, Set<String> normalizedUserIds) {
    if (item == null || normalizedUserIds == null || normalizedUserIds.isEmpty()) {
      return false;
    }
    String proposer = sanitizeUserId(item.proposerUserId());
    if (proposer != null && normalizedUserIds.contains(proposer)) {
      return true;
    }
    if (item.panelists() == null || item.panelists().isEmpty()) {
      return false;
    }
    for (CfpPanelist panelist : item.panelists()) {
      if (panelist == null) {
        continue;
      }
      String panelistUserId = sanitizeUserId(panelist.userId());
      if (panelistUserId != null && normalizedUserIds.contains(panelistUserId)) {
        return true;
      }
    }
    return false;
  }

  public record PanelistInput(String name, String email, String userId) {}

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

  public record EventStats(int total, Map<CfpSubmissionStatus, Integer> countsByStatus, Instant latestUpdatedAt) {
    public static EventStats empty() {
      EnumMap<CfpSubmissionStatus, Integer> emptyCounts = new EnumMap<>(CfpSubmissionStatus.class);
      for (CfpSubmissionStatus status : CfpSubmissionStatus.values()) {
        emptyCounts.put(status, 0);
      }
      return new EventStats(0, Map.copyOf(emptyCounts), null);
    }
  }

  public record MineStats(
      int total, Map<CfpSubmissionStatus, Integer> countsByStatus, int distinctEvents, Instant latestUpdatedAt) {
    public static MineStats empty() {
      EnumMap<CfpSubmissionStatus, Integer> emptyCounts = new EnumMap<>(CfpSubmissionStatus.class);
      for (CfpSubmissionStatus status : CfpSubmissionStatus.values()) {
        emptyCounts.put(status, 0);
      }
      return new MineStats(0, Map.copyOf(emptyCounts), 0, null);
    }
  }
}
