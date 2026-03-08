package com.scanales.eventflow.volunteers;

import com.scanales.eventflow.insights.DevelopmentInsightsLedgerService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/** Records volunteer lifecycle events in the insights ledger without coupling controllers to internals. */
@ApplicationScoped
public class VolunteerInsightsService {
  private static final Logger LOG = Logger.getLogger(VolunteerInsightsService.class);

  @Inject DevelopmentInsightsLedgerService insightsLedger;
  @Inject EventService eventService;

  private final Set<String> initializedInitiatives = ConcurrentHashMap.newKeySet();
  private final Object initLock = new Object();

  @PostConstruct
  void init() {
    try {
      insightsLedger.listInitiatives(5000, 0).stream()
          .map(item -> item.initiativeId())
          .filter(item -> item != null && !item.isBlank())
          .forEach(initializedInitiatives::add);
    } catch (IllegalStateException e) {
      // Insights can be disabled in some environments; keep no-op behavior.
      LOG.debug("volunteer_insights_init_skipped", e);
    } catch (Exception e) {
      LOG.warn("volunteer_insights_init_failed", e);
    }
  }

  public void recordApplicationSubmitted(VolunteerApplication application) {
    append(
        application,
        "VOLUNTEER_SUBMITTED",
        Map.of("status", safeStatus(application), "application_id", safe(application != null ? application.id() : null)));
  }

  public void recordApplicationUpdated(VolunteerApplication application) {
    append(
        application,
        "VOLUNTEER_UPDATED",
        Map.of("status", safeStatus(application), "application_id", safe(application != null ? application.id() : null)));
  }

  public void recordApplicationWithdrawn(VolunteerApplication application) {
    append(
        application,
        "VOLUNTEER_WITHDRAWN",
        Map.of("status", safeStatus(application), "application_id", safe(application != null ? application.id() : null)));
  }

  public void recordStatusChange(VolunteerApplication before, VolunteerApplication after) {
    String from = before != null && before.status() != null ? before.status().apiValue() : "unknown";
    String to = after != null && after.status() != null ? after.status().apiValue() : "unknown";
    append(
        after,
        "VOLUNTEER_STATUS_" + toUpperSnake(to),
        Map.of(
            "from_status", safe(from),
            "to_status", safe(to),
            "application_id", safe(after != null ? after.id() : null)));
  }

  public void recordRatingUpdated(VolunteerApplication application) {
    append(
        application,
        "VOLUNTEER_RATING_UPDATED",
        Map.of("status", safeStatus(application), "application_id", safe(application != null ? application.id() : null)));
  }

  public void recordLoungePost(VolunteerLoungeMessage message) {
    if (message == null || message.eventId() == null || message.eventId().isBlank()) {
      return;
    }
    String initiativeId = initiativeId(message.eventId());
    ensureInitiativeStarted(initiativeId, message.eventId());
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("module", "event-volunteers");
    metadata.put("event_id", safe(message.eventId()));
    metadata.put("message_id", safe(message.id()));
    metadata.put("parent_id", safe(message.parentId()));
    metadata.put("user_id", safe(message.userId()));
    appendSafe(initiativeId, "VOLUNTEER_LOUNGE_POSTED", metadata);
  }

  public void recordLoungeAnnouncement(VolunteerLoungeMessage message) {
    if (message == null || message.eventId() == null || message.eventId().isBlank()) {
      return;
    }
    String initiativeId = initiativeId(message.eventId());
    ensureInitiativeStarted(initiativeId, message.eventId());
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("module", "event-volunteers");
    metadata.put("event_id", safe(message.eventId()));
    metadata.put("message_id", safe(message.id()));
    metadata.put("user_id", safe(message.userId()));
    appendSafe(initiativeId, "VOLUNTEER_LOUNGE_ANNOUNCEMENT", metadata);
  }

  private void append(VolunteerApplication application, String type, Map<String, String> metadata) {
    if (application == null || application.eventId() == null || application.eventId().isBlank()) {
      return;
    }
    String initiativeId = initiativeId(application.eventId());
    ensureInitiativeStarted(initiativeId, application.eventId());
    Map<String, String> payload = new LinkedHashMap<>();
    payload.put("module", "event-volunteers");
    payload.put("event_id", safe(application.eventId()));
    payload.put("application_id", safe(application.id()));
    payload.put("user_id", safe(application.applicantUserId()));
    payload.put("status", safeStatus(application));
    if (metadata != null && !metadata.isEmpty()) {
      metadata.forEach((key, value) -> payload.put(safeKey(key), safe(value)));
    }
    appendSafe(initiativeId, type, payload);
  }

  private void ensureInitiativeStarted(String initiativeId, String eventId) {
    if (initiativeId == null || initiativeId.isBlank() || initializedInitiatives.contains(initiativeId)) {
      return;
    }
    synchronized (initLock) {
      if (initializedInitiatives.contains(initiativeId)) {
        return;
      }
      Event event = eventService != null ? eventService.getEvent(eventId) : null;
      String title =
          event != null && event.getTitle() != null && !event.getTitle().isBlank()
              ? "Volunteers · " + event.getTitle()
              : "Volunteers · " + eventId;
      Map<String, String> metadata = new LinkedHashMap<>();
      metadata.put("module", "event-volunteers");
      metadata.put("event_id", safe(eventId));
      metadata.put("source", "homedir-runtime");
      metadata.put("definition_started_at", Instant.now().toString());
      try {
        insightsLedger.startInitiative(initiativeId, title, Instant.now().toString(), metadata);
        initializedInitiatives.add(initiativeId);
      } catch (IllegalStateException e) {
        LOG.debug("volunteer_insights_disabled", e);
      } catch (Exception e) {
        LOG.warn("volunteer_insights_start_failed", e);
      }
    }
  }

  private void appendSafe(String initiativeId, String type, Map<String, String> metadata) {
    try {
      insightsLedger.append(initiativeId, type, metadata);
      initializedInitiatives.add(initiativeId);
    } catch (IllegalStateException e) {
      LOG.debug("volunteer_insights_disabled", e);
    } catch (Exception e) {
      LOG.warn("volunteer_insights_append_failed", e);
    }
  }

  private static String initiativeId(String eventId) {
    return "event-volunteers-" + toSlug(eventId);
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

  private static String safeStatus(VolunteerApplication item) {
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
