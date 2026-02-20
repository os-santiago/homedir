package com.scanales.eventflow.service;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.EventType;
import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.model.TalkInfo;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/** Simple in-memory store for events. */
@ApplicationScoped
public class EventService {
  private static final String DEVOPSDAYS_2026_ID = "devopsdays-santiago-2026";
  private static final String DEVOPSDAYS_MAIN_STAGE = "main-stage";

  /**
   * Global cache of events shared by all sessions. Using a static map ensures the
   * same {@code
   * Event} instance is returned for a given id, reducing memory usage and
   * avoiding unnecessary
   * object duplication. The ConcurrentHashMap provides lock-free reads and
   * efficient updates for a
   * high performance setup.
   */
  private static final Map<String, Event> events = new ConcurrentHashMap<>();

  @Inject
  PersistenceService persistence;

  private static final Logger LOG = Logger.getLogger(EventService.class);

  @PostConstruct
  void init() {
    events.putAll(persistence.loadEvents());
    boolean seeded = false;
    for (Event event : events.values()) {
      ensureDefaults(event);
      seeded = ensureDevOpsDaysDraftAgenda(event) || seeded;
    }
    if (seeded) {
      persistence.saveEvents(new ConcurrentHashMap<>(events));
      LOG.infov("Applied draft agenda seed for event {0}", DEVOPSDAYS_2026_ID);
    }
    LOG.infof("Loaded %d events into memory", events.size());
  }

  public List<Event> listEvents() {
    return new ArrayList<>(events.values());
  }

  public Event getEvent(String id) {
    return events.get(id);
  }

  public void saveEvent(Event event) {
    ensureDefaults(event);
    ensureDevOpsDaysDraftAgenda(event);
    events.put(event.getId(), event);
    persistence.saveEvents(new ConcurrentHashMap<>(events));
  }

  public void deleteEvent(String id) {
    events.remove(id);
    persistence.saveEvents(new ConcurrentHashMap<>(events));
  }

  public void saveScenario(String eventId, Scenario scenario) {
    Event event = events.get(eventId);
    if (event == null) {
      return;
    }
    event.getScenarios().removeIf(s -> s.getId().equals(scenario.getId()));
    event.getScenarios().add(scenario);
    persistence.saveEvents(new ConcurrentHashMap<>(events));
  }

  public void deleteScenario(String eventId, String scenarioId) {
    Event event = events.get(eventId);
    if (event != null) {
      event.getScenarios().removeIf(s -> s.getId().equals(scenarioId));
      persistence.saveEvents(new ConcurrentHashMap<>(events));
    }
  }

  public void saveTalk(String eventId, Talk talk) {
    Event event = events.get(eventId);
    if (event == null) {
      return;
    }
    event.getAgenda().removeIf(t -> t.getId().equals(talk.getId()));
    event.getAgenda().add(talk);
    event
        .getAgenda()
        .sort(
            java.util.Comparator.comparingInt(Talk::getDay)
                .thenComparing(
                    Talk::getStartTime,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
    persistence.saveEvents(new ConcurrentHashMap<>(events));
  }

  public void deleteTalk(String eventId, String talkId) {
    Event event = events.get(eventId);
    if (event != null) {
      event.getAgenda().removeIf(t -> t.getId().equals(talkId));
      persistence.saveEvents(new ConcurrentHashMap<>(events));
    }
  }

  /** Clears all stored events (testing only). */
  public void reset() {
    events.clear();
    persistence.saveEvents(new ConcurrentHashMap<>(events));
  }

  /**
   * Checks whether the given talk overlaps with an existing one in the same
   * event, day and
   * scenario.
   *
   * @return {@code true} if an overlap is detected
   */
  public boolean hasOverlap(String eventId, Talk talk) {
    return findOverlap(eventId, talk) != null;
  }

  /**
   * Returns an existing talk that overlaps with the given one or {@code null} if
   * none. Two talks
   * overlap when they occur on the same day and scenario and their times
   * intersect.
   */
  public Talk findOverlap(String eventId, Talk talk) {
    Event event = events.get(eventId);
    if (event == null || talk.getStartTime() == null) {
      return null;
    }
    java.time.LocalTime start = talk.getStartTime();
    java.time.LocalTime end = talk.getEndTime();
    return event.getAgenda().stream()
        .filter(
            t -> !t.getId().equals(talk.getId())
                && t.getDay() == talk.getDay()
                && t.getLocation() != null
                && t.getLocation().equals(talk.getLocation()))
        .filter(
            t -> {
              java.time.LocalTime s = t.getStartTime();
              java.time.LocalTime e = t.getEndTime();
              if (s == null || e == null) {
                return false;
              }
              return start.isBefore(e) && end.isAfter(s);
            })
        .findFirst()
        .orElse(null);
  }

  public Scenario findScenario(String scenarioId) {
    return events.values().stream()
        .flatMap(e -> e.getScenarios().stream())
        .filter(s -> s.getId().equals(scenarioId))
        .findFirst()
        .orElse(null);
  }

  public Talk findTalk(String talkId) {
    return events.values().stream()
        .flatMap(e -> e.getAgenda().stream())
        .filter(t -> t.getId().equals(talkId))
        .findFirst()
        .orElse(null);
  }

  /**
   * Returns the talk with the given id within the specified event or {@code null}
   * if not found.
   */
  public Talk findTalk(String eventId, String talkId) {
    Event event = events.get(eventId);
    if (event == null) {
      return null;
    }
    return event.getAgenda().stream()
        .filter(t -> t.getId().equals(talkId))
        .findFirst()
        .orElse(null);
  }

  /**
   * Returns the event that contains the given scenario or {@code null} if none.
   */
  public Event findEventByScenario(String scenarioId) {
    return events.values().stream()
        .filter(e -> e.getScenarios().stream().anyMatch(s -> s.getId().equals(scenarioId)))
        .findFirst()
        .orElse(null);
  }

  /** Returns the event that includes the provided talk id or {@code null}. */
  public Event findEventByTalk(String talkId) {
    return events.values().stream()
        .filter(e -> e.getAgenda().stream().anyMatch(t -> t.getId().equals(talkId)))
        .findFirst()
        .orElse(null);
  }

  /** Returns all events that include the provided talk id. */
  public List<Event> findEventsByTalk(String talkId) {
    return events.values().stream()
        .filter(e -> e.getAgenda().stream().anyMatch(t -> t.getId().equals(talkId)))
        .sorted(java.util.Comparator.comparing(Event::getId))
        .toList();
  }

  /**
   * Returns a {@link TalkInfo} containing the talk and its parent event or
   * {@code null}.
   */
  public TalkInfo findTalkInfo(String talkId) {
    Talk talk = findTalk(talkId);
    if (talk == null) {
      return null;
    }
    Event event = findEventByTalk(talkId);
    return new TalkInfo(talk, event);
  }

  /**
   * Returns all talk instances matching the given id across all events. Useful
   * when the same talk
   * is scheduled multiple times.
   */
  public List<Talk> findTalkOccurrences(String talkId) {
    return events.values().stream()
        .flatMap(e -> e.getAgenda().stream().filter(t -> t.getId().equals(talkId)))
        .sorted(
            java.util.Comparator.comparingInt(Talk::getDay)
                .thenComparing(
                    Talk::getStartTime,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
        .toList();
  }

  /**
   * Returns all instances of a talk within the specified event ordered by day and
   * time.
   */
  public List<Talk> findTalkOccurrences(String eventId, String talkId) {
    Event event = events.get(eventId);
    if (event == null) {
      return java.util.List.of();
    }
    return event.getAgenda().stream()
        .filter(t -> t.getId().equals(talkId))
        .sorted(
            java.util.Comparator.comparingInt(Talk::getDay)
                .thenComparing(
                    Talk::getStartTime,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
        .toList();
  }

  /**
   * Returns the list of talks scheduled in the given scenario ordered by day and
   * time.
   */
  public List<Talk> findTalksForScenario(String scenarioId) {
    return events.values().stream()
        .flatMap(e -> e.getAgenda().stream())
        .filter(t -> scenarioId.equals(t.getLocation()))
        .sorted(
            java.util.Comparator.comparingInt(Talk::getDay)
                .thenComparing(
                    Talk::getStartTime,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
        .toList();
  }

  public List<Event> findPastEvents(int limit) {
    return events.values().stream()
        .filter(e -> e.getDate() != null)
        .filter(e -> e.getDate().isBefore(java.time.LocalDate.now()))
        .sorted(java.util.Comparator.comparing(Event::getDate).reversed())
        .limit(limit)
        .toList();
  }

  /** Reloads events from persistence replacing the current cache. */
  public void reload() {
    events.clear();
    events.putAll(persistence.loadEvents());
    boolean seeded = false;
    for (Event event : events.values()) {
      ensureDefaults(event);
      seeded = ensureDevOpsDaysDraftAgenda(event) || seeded;
    }
    if (seeded) {
      persistence.saveEvents(new ConcurrentHashMap<>(events));
    }
  }

  private void ensureDefaults(Event event) {
    if (event.getType() == null) {
      event.setType(EventType.OTHER);
    }
  }

  private boolean ensureDevOpsDaysDraftAgenda(Event event) {
    if (event == null || event.getId() == null || !DEVOPSDAYS_2026_ID.equalsIgnoreCase(event.getId())) {
      return false;
    }
    if (event.getAgenda() != null && !event.getAgenda().isEmpty()) {
      return false;
    }
    if (event.getScenarios() == null) {
      event.setScenarios(new ArrayList<>());
    }
    String scenarioId =
        event.getScenarios().stream()
            .map(Scenario::getId)
            .filter(id -> id != null && !id.isBlank())
            .findFirst()
            .orElse(null);
    if (scenarioId == null) {
      Scenario mainStage = new Scenario(DEVOPSDAYS_MAIN_STAGE, "Main Stage");
      mainStage.setFeatures("Draft agenda seed");
      event.getScenarios().add(mainStage);
      scenarioId = DEVOPSDAYS_MAIN_STAGE;
    }
    List<Talk> seededTalks = new ArrayList<>();
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-keynote-platform-velocity",
            "Platform Engineering in the Age of AI Agents",
            "How platform teams are evolving delivery, reliability and developer experience in 2026.",
            scenarioId,
            1,
            "09:30",
            30));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-devsecops-pipelines",
            "DevSecOps by Default: Secure Pipelines at Scale",
            "Practical controls to shift security left without slowing product delivery.",
            scenarioId,
            1,
            "10:15",
            30));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-kubernetes-sre",
            "Kubernetes SRE Playbook for High-Traffic Systems",
            "Incident patterns, SLOs and production hardening for cloud-native teams.",
            scenarioId,
            1,
            "11:15",
            30));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-idp-workshop",
            "Building an Internal Developer Platform that Teams Actually Use",
            "A field guide to rollout strategy, golden paths and adoption metrics.",
            scenarioId,
            1,
            "14:00",
            45));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-observability-aiops",
            "AIOps + Observability: From Alert Noise to Actionable Signals",
            "Using telemetry and automation to reduce MTTR and pager fatigue.",
            scenarioId,
            1,
            "15:15",
            30));
    event.setAgenda(seededTalks);
    if (event.getDays() <= 0) {
      event.setDays(1);
    }
    return true;
  }

  private Talk buildSeedTalk(
      String id,
      String name,
      String description,
      String scenarioId,
      int day,
      String startTime,
      int durationMinutes) {
    Talk talk = new Talk(id, name);
    talk.setDescription(description);
    talk.setLocation(scenarioId);
    talk.setDay(day);
    talk.setStartTimeStr(startTime);
    talk.setDurationMinutes(durationMinutes);
    return talk;
  }

  public List<Event> findUpcomingEvents(int limit) {
    return events.values().stream()
        .filter(e -> e.getDate() != null)
        .filter(e -> !e.getDate().isBefore(java.time.LocalDate.now()))
        .sorted(java.util.Comparator.comparing(Event::getDate))
        .limit(limit)
        .toList();
  }
}
