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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/** Simple in-memory store for events. */
@ApplicationScoped
public class EventService {
  private static final String DEVOPSDAYS_2026_ID = "devopsdays-santiago-2026";
  private static final String DEVOPSDAYS_MAIN_STAGE = "main-stage";
  private static final Set<String> LEGACY_DEVOPSDAYS_SEED_IDS =
      Set.of(
          "dod-2026-keynote-platform-velocity",
          "dod-2026-devsecops-pipelines",
          "dod-2026-kubernetes-sre",
          "dod-2026-idp-workshop",
          "dod-2026-observability-aiops",
          "dod-2026-day1-ai-native-engineering-copilots",
          "dod-2026-day1-platform-engineering-idp",
          "dod-2026-day1-coffee-break",
          "dod-2026-day1-security-supply-chain",
          "dod-2026-day1-finops-greenops",
          "dod-2026-day1-lunch-break",
          "dod-2026-day1-sre-observability-aiops",
          "dod-2026-day1-data-ai-platforms-llmops",
          "dod-2026-day1-networking-break",
          "dod-2026-day1-devex-innersource-flow",
          "dod-2026-day2-kubernetes-runtime-multi-cluster",
          "dod-2026-day2-edge-iot-realtime",
          "dod-2026-day2-coffee-break",
          "dod-2026-day2-zero-trust-identity-secrets",
          "dod-2026-day2-automation-orchestration-gitops",
          "dod-2026-day2-lunch-break",
          "dod-2026-day2-ai-governance-safety",
          "dod-2026-day2-apis-event-driven-architecture",
          "dod-2026-day2-networking-break",
          "dod-2026-day2-tech-leadership-product-delivery");

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
    boolean shouldSeed = event.getAgenda() == null || event.getAgenda().isEmpty();
    if (!shouldSeed && isLegacyDevOpsDaysSeedAgenda(event.getAgenda())) {
      shouldSeed = true;
    }
    if (!shouldSeed) {
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
            "dod-2026-day1-welcome",
            "Welcome",
            "Opening and logistics for day 1.",
            scenarioId,
            1,
            "09:00",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-keynote",
            "Keynote: 2026 Technology Outlook",
            "Market outlook for technology, open source and AI adoption in 2026.",
            scenarioId,
            1,
            "09:30",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-ai-native-engineering-copilots",
            "Category: AI-native Engineering & Copilots",
            "Developer copilots, autonomous coding workflows and guardrailed AI delivery.",
            scenarioId,
            1,
            "10:00",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-platform-engineering-idp",
            "Category: Platform Engineering & Internal Developer Platforms",
            "Golden paths, self-service infrastructure and platform product practices.",
            scenarioId,
            1,
            "10:30",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-security-supply-chain",
            "Category: Cloud Native Security & Software Supply Chain",
            "Runtime security, SBOM, supply-chain controls and policy automation.",
            scenarioId,
            1,
            "11:00",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-coffee-break",
            "Coffee Break",
            "Networking and transition block.",
            scenarioId,
            1,
            "11:30",
            30,
            true));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-sre-observability-aiops",
            "Category: SRE, Observability & AIOps",
            "Resilience engineering, incident response, telemetry strategy and AI-assisted operations.",
            scenarioId,
            1,
            "12:00",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-data-ai-platforms-llmops",
            "Category: Data/AI Platforms, MLOps & LLMOps",
            "Operating data and model platforms with reliability, governance and production safety.",
            scenarioId,
            1,
            "12:30",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-lunch-break",
            "Lunch Break",
            "Lunch and hallway conversations.",
            scenarioId,
            1,
            "13:00",
            30,
            true));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-finops-greenops",
            "Category: FinOps, GreenOps & Cost Optimization",
            "Balancing cloud cost, sustainability and performance for modern workloads.",
            scenarioId,
            1,
            "13:30",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-devex-innersource-flow",
            "Category: Developer Experience, InnerSource & Flow Metrics",
            "Improving team productivity and delivery outcomes through DevEx practices.",
            scenarioId,
            1,
            "14:00",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-networking-break",
            "Networking Break",
            "Community networking slot.",
            scenarioId,
            1,
            "14:30",
            30,
            true));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-automation-gitops",
            "Category: Automation, Orchestration & GitOps at Scale",
            "Reliable automation strategies for release pipelines and fleet operations.",
            scenarioId,
            1,
            "15:00",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-apis-event-driven",
            "Category: Modern APIs, Integration & Event-driven Architecture",
            "Composable architectures for platform products and ecosystem interoperability.",
            scenarioId,
            1,
            "15:30",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day1-tech-leadership",
            "Category: Technical Leadership, Team Topologies & Product Delivery",
            "Scaling engineering organizations with clear ownership and execution discipline.",
            scenarioId,
            1,
            "16:00",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-welcome",
            "Welcome",
            "Opening and logistics for day 2.",
            scenarioId,
            2,
            "09:00",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-keynote",
            "Keynote: Platform + AI in Production",
            "Lessons learned moving AI-powered platforms into production at scale.",
            scenarioId,
            2,
            "09:30",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-kubernetes-runtime-multi-cluster",
            "Category: Kubernetes Runtime Evolution & Multi-cluster Operations",
            "Runtime innovation, workload portability and operations patterns for large-scale clusters.",
            scenarioId,
            2,
            "10:00",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-edge-iot-realtime",
            "Category: Edge, IoT & Real-time Platforms",
            "Distributed compute patterns for low-latency systems and connected ecosystems.",
            scenarioId,
            2,
            "10:30",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-zero-trust-identity-secrets",
            "Category: Zero Trust Identity, Access & Secrets",
            "Identity-first security controls for apps, workloads and platform operators.",
            scenarioId,
            2,
            "11:00",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-coffee-break",
            "Coffee Break",
            "Networking and transition block.",
            scenarioId,
            2,
            "11:30",
            30,
            true));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-ai-governance-safety",
            "Category: AI Governance, Safety & Responsible Adoption",
            "Practical controls for responsible AI adoption in enterprise and open communities.",
            scenarioId,
            2,
            "12:00",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-open-source-community-engineering",
            "Category: Open Source Program Offices & Community Engineering",
            "Scaling open source contribution models, governance and ecosystem collaboration.",
            scenarioId,
            2,
            "12:30",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-lunch-break",
            "Lunch Break",
            "Lunch and hallway conversations.",
            scenarioId,
            2,
            "13:00",
            30,
            true));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-reliability-chaos",
            "Category: Reliability Engineering, Resilience & Chaos",
            "Designing and validating resilient systems under real production pressure.",
            scenarioId,
            2,
            "13:30",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-data-platform-observability",
            "Category: Data Platform Reliability & Observability",
            "Operational patterns for trustworthy data products and pipelines.",
            scenarioId,
            2,
            "14:00",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-networking-break",
            "Networking Break",
            "Community networking slot.",
            scenarioId,
            2,
            "14:30",
            30,
            true));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-product-analytics-experimentation",
            "Category: Product Analytics, Experimentation & Delivery Signals",
            "Using delivery metrics and experimentation loops to accelerate outcomes.",
            scenarioId,
            2,
            "15:00",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-cloud-performance-governance",
            "Category: Cloud Performance, Cost Governance & Capacity Planning",
            "Balancing performance, cost and capacity for sustained platform growth.",
            scenarioId,
            2,
            "15:30",
            30,
            false));
    seededTalks.add(
        buildSeedTalk(
            "dod-2026-day2-architecture-decisions",
            "Category: Architecture Reviews & Technical Decision Records",
            "Lightweight decision governance to keep teams aligned and scalable.",
            scenarioId,
            2,
            "16:00",
            30,
            false));
    event.setAgenda(seededTalks);
    if (event.getDays() < 2) {
      event.setDays(2);
    }
    return true;
  }

  private boolean isLegacyDevOpsDaysSeedAgenda(List<Talk> agenda) {
    if (agenda == null || agenda.isEmpty()) {
      return false;
    }
    return agenda.stream()
        .map(Talk::getId)
        .allMatch(id -> id != null && LEGACY_DEVOPSDAYS_SEED_IDS.contains(id));
  }

  private Talk buildSeedTalk(
      String id,
      String name,
      String description,
      String scenarioId,
      int day,
      String startTime,
      int durationMinutes,
      boolean breakSlot) {
    Talk talk = new Talk(id, name);
    talk.setDescription(description);
    talk.setLocation(scenarioId);
    talk.setDay(day);
    talk.setStartTimeStr(startTime);
    talk.setDurationMinutes(durationMinutes);
    talk.setBreak(breakSlot);
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
