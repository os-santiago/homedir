package com.scanales.homedir.cfp;

import com.scanales.homedir.insights.DevelopmentInsightsLedgerService;
import com.scanales.homedir.model.Event;
import com.scanales.homedir.service.EventService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/** Records CFP lifecycle events in insights ledger and seeds pre-existing CFP data once. */
@ApplicationScoped
public class CfpInsightsService {
  private static final Logger LOG = Logger.getLogger(CfpInsightsService.class);

  @Inject DevelopmentInsightsLedgerService insightsLedger;
  @Inject EventService eventService;
  @Inject CfpSubmissionService cfpSubmissionService;

  private final Set<String> initializedInitiatives = ConcurrentHashMap.newKeySet();
  private final Object initLock = new Object();

  @PostConstruct
  void init() {
    loadKnownInitiatives();
    seedFromExistingSubmissions();
  }

  public void recordSubmissionCreated(CfpSubmission submission) {
    append(
        submission,
        "CFP_SUBMITTED",
        Map.of(
            "submission_id", safe(submission != null ? submission.id() : null),
            "status", safeStatus(submission)));
  }

  public void recordStatusChange(CfpSubmission before, CfpSubmission after) {
    String previous = safeStatus(before);
    String current = safeStatus(after);
    append(
        after,
        "CFP_STATUS_" + toUpperSnake(current),
        Map.of(
            "submission_id", safe(after != null ? after.id() : null),
            "from_status", previous,
            "to_status", current));
  }

  public void recordRatingUpdated(CfpSubmission submission) {
    append(
        submission,
        "CFP_RATING_UPDATED",
        Map.of(
            "submission_id", safe(submission != null ? submission.id() : null),
            "status", safeStatus(submission)));
  }

  public void recordPromoted(CfpSubmission submission, String speakerId, String talkId) {
    append(
        submission,
        "CFP_PROMOTED",
        Map.of(
            "submission_id", safe(submission != null ? submission.id() : null),
            "speaker_id", safe(speakerId),
            "talk_id", safe(talkId),
            "status", safeStatus(submission)));
  }

  private void loadKnownInitiatives() {
    try {
      insightsLedger.listInitiatives(5000, 0).stream()
          .map(item -> item.initiativeId())
          .filter(item -> item != null && !item.isBlank())
          .forEach(initializedInitiatives::add);
    } catch (IllegalStateException e) {
      LOG.debug("cfp_insights_init_skipped", e);
    } catch (Exception e) {
      LOG.warn("cfp_insights_init_failed", e);
    }
  }

  private void seedFromExistingSubmissions() {
    List<Event> events = eventService != null ? eventService.listEvents() : List.of();
    for (Event event : events) {
      if (event == null || event.getId() == null || event.getId().isBlank()) {
        continue;
      }
      String eventId = event.getId();
      String initiativeId = initiativeId(eventId);
      if (initializedInitiatives.contains(initiativeId)) {
        continue;
      }
      List<CfpSubmission> submissions =
          cfpSubmissionService.listByEventAll(
              eventId, Optional.empty(), CfpSubmissionService.SortOrder.CREATED_DESC);
      if (submissions.isEmpty()) {
        continue;
      }
      ensureInitiativeStarted(initiativeId, eventId, event.getTitle());
      for (int i = submissions.size() - 1; i >= 0; i--) {
        CfpSubmission submission = submissions.get(i);
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("module", "event-cfp");
        metadata.put("event_id", safe(eventId));
        metadata.put("submission_id", safe(submission.id()));
        metadata.put("user_id", safe(submission.proposerUserId()));
        metadata.put("status", safeStatus(submission));
        metadata.put("created_at", safe(submission.createdAt() != null ? submission.createdAt().toString() : null));
        appendSafe(initiativeId, "CFP_IMPORTED", metadata);
      }
    }
  }

  private void append(CfpSubmission submission, String type, Map<String, String> metadata) {
    if (submission == null || submission.eventId() == null || submission.eventId().isBlank()) {
      return;
    }
    String eventId = submission.eventId();
    String initiativeId = initiativeId(eventId);
    ensureInitiativeStarted(initiativeId, eventId, null);
    Map<String, String> payload = new LinkedHashMap<>();
    payload.put("module", "event-cfp");
    payload.put("event_id", safe(eventId));
    payload.put("submission_id", safe(submission.id()));
    payload.put("user_id", safe(submission.proposerUserId()));
    payload.put("status", safeStatus(submission));
    if (metadata != null && !metadata.isEmpty()) {
      metadata.forEach((key, value) -> payload.put(safeKey(key), safe(value)));
    }
    appendSafe(initiativeId, type, payload);
  }

  private void ensureInitiativeStarted(String initiativeId, String eventId, String eventTitleHint) {
    if (initiativeId == null || initiativeId.isBlank() || initializedInitiatives.contains(initiativeId)) {
      return;
    }
    synchronized (initLock) {
      if (initializedInitiatives.contains(initiativeId)) {
        return;
      }
      Event event = eventService != null ? eventService.getEvent(eventId) : null;
      String eventTitle =
          event != null && event.getTitle() != null && !event.getTitle().isBlank()
              ? event.getTitle()
              : (eventTitleHint != null && !eventTitleHint.isBlank() ? eventTitleHint : eventId);
      String title = "CFP · " + eventTitle;
      Map<String, String> metadata = new LinkedHashMap<>();
      metadata.put("module", "event-cfp");
      metadata.put("event_id", safe(eventId));
      metadata.put("source", "homedir-runtime");
      metadata.put("definition_started_at", Instant.now().toString());
      try {
        insightsLedger.startInitiative(initiativeId, title, Instant.now().toString(), metadata);
        initializedInitiatives.add(initiativeId);
      } catch (IllegalStateException e) {
        LOG.debug("cfp_insights_disabled", e);
      } catch (Exception e) {
        LOG.warn("cfp_insights_start_failed", e);
      }
    }
  }

  private void appendSafe(String initiativeId, String type, Map<String, String> metadata) {
    try {
      insightsLedger.append(initiativeId, type, metadata);
      initializedInitiatives.add(initiativeId);
    } catch (IllegalStateException e) {
      LOG.debug("cfp_insights_disabled", e);
    } catch (Exception e) {
      LOG.warn("cfp_insights_append_failed", e);
    }
  }

  public static String initiativeId(String eventId) {
    return "event-cfp-" + toSlug(eventId);
  }

  private static String toSlug(String raw) {
    if (raw == null || raw.isBlank()) {
      return "unknown";
    }
    String source = raw.trim().toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder(source.length());
    boolean lastDash = false;
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      boolean alphaNum = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
      if (alphaNum) {
        out.append(c);
        lastDash = false;
      } else if (!lastDash && out.length() > 0) {
        out.append('-');
        lastDash = true;
      }
    }
    int end = out.length();
    if (end > 0 && out.charAt(end - 1) == '-') {
      out.setLength(end - 1);
    }
    return out.isEmpty() ? "unknown" : out.toString();
  }

  private static String toUpperSnake(String raw) {
    if (raw == null || raw.isBlank()) {
      return "UNKNOWN";
    }
    return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
  }

  private static String safeStatus(CfpSubmission item) {
    if (item == null || item.status() == null) {
      return "unknown";
    }
    return safe(item.status().apiValue());
  }

  private static String safe(String raw) {
    if (raw == null || raw.isBlank()) {
      return "n/a";
    }
    String value = raw.trim();
    return value.length() > 240 ? value.substring(0, 240) : value;
  }

  private static String safeKey(String raw) {
    if (raw == null || raw.isBlank()) {
      return "meta";
    }
    String source = raw.trim().toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder(source.length());
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      boolean allowed =
          (c >= 'a' && c <= 'z')
              || (c >= '0' && c <= '9')
              || c == '_'
              || c == '-'
              || c == '.';
      out.append(allowed ? c : '_');
    }
    String key = out.toString();
    if (key.length() > 80) {
      key = key.substring(0, 80);
    }
    return key.isBlank() ? "meta" : key;
  }
}
