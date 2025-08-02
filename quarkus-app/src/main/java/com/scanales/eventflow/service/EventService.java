package com.scanales.eventflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

import jakarta.enterprise.context.ApplicationScoped;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Talk;
import org.jboss.logging.Logger;

/** Simple in-memory store for events. */
@ApplicationScoped
public class EventService {

    /**
     * Global cache of events shared by all sessions. A synchronized
     * {@link LinkedHashMap} with access order provides LRU eviction to
     * avoid unbounded memory growth while still returning the same
     * {@code Event} instance for a given id.
     */
    private static final Logger LOG = Logger.getLogger(EventService.class);
    private static final String PREFIX = "[EVENT] ";

    private static final int MAX_EVENTS = 100;
    private static final Map<String, Event> events = Collections.synchronizedMap(
            new LinkedHashMap<String, Event>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Event> eldest) {
                    return size() > MAX_EVENTS;
                }
            });

    public List<Event> listEvents() {
        LOG.debug(PREFIX + "EventService.listEvents(): listando eventos");
        return new ArrayList<>(events.values());
    }

    public Event getEvent(String id) {
        LOG.debugf(PREFIX + "EventService.getEvent(): id=%s", id);
        return events.get(id);
    }

    /**
     * Inserts the provided event using the given identifier without additional
     * processing. Existing entries with the same id are replaced.
     */
    public void putEvent(String id, Event event) {
        events.put(id, event);
        LOG.debugf(PREFIX + "EventService.putEvent(): id=%s", id);
    }

    public void saveEvent(Event event) {
        events.put(event.getId(), event);
        LOG.infof(PREFIX + "EventService.saveEvent(): Evento %s guardado", event.getId());
    }

    public void deleteEvent(String id) {
        events.remove(id);
        LOG.infof(PREFIX + "EventService.deleteEvent(): Evento %s eliminado", id);
    }

    public void saveScenario(String eventId, Scenario scenario) {
        Event event = events.get(eventId);
        if (event == null) {
            return;
        }
        event.getScenarios().removeIf(s -> s.getId().equals(scenario.getId()));
        event.getScenarios().add(scenario);
        LOG.infof(PREFIX + "EventService.saveScenario(): Escenario %s guardado para evento %s", scenario.getId(), eventId);
    }

    public void deleteScenario(String eventId, String scenarioId) {
        Event event = events.get(eventId);
        if (event != null) {
            event.getScenarios().removeIf(s -> s.getId().equals(scenarioId));
            LOG.infof(PREFIX + "EventService.deleteScenario(): Escenario %s eliminado de evento %s", scenarioId, eventId);
        }
    }

    public void saveTalk(String eventId, Talk talk) {
        Event event = events.get(eventId);
        if (event == null) {
            return;
        }
        event.getAgenda().removeIf(t -> t.getId().equals(talk.getId()));
        event.getAgenda().add(talk);
        LOG.infof(PREFIX + "EventService.saveTalk(): Charla %s guardada para evento %s", talk.getId(), eventId);
    }

    public void deleteTalk(String eventId, String talkId) {
        Event event = events.get(eventId);
        if (event != null) {
            event.getAgenda().removeIf(t -> t.getId().equals(talkId));
            LOG.infof(PREFIX + "EventService.deleteTalk(): Charla %s eliminada de evento %s", talkId, eventId);
        }
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

    /** Returns the event that contains the given scenario or {@code null} if none. */
    public Event findEventByScenario(String scenarioId) {
        return events.values().stream()
                .filter(e -> e.getScenarios().stream()
                        .anyMatch(s -> s.getId().equals(scenarioId)))
                .findFirst()
                .orElse(null);
    }

    /** Returns the event that includes the provided talk id or {@code null}. */
    public Event findEventByTalk(String talkId) {
        return events.values().stream()
                .filter(e -> e.getAgenda().stream()
                        .anyMatch(t -> t.getId().equals(talkId)))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns all talk instances matching the given id across all events.
     * Useful when the same talk is scheduled multiple times.
     */
    public List<Talk> findTalkOccurrences(String talkId) {
        return events.values().stream()
                .flatMap(e -> e.getAgenda().stream()
                        .filter(t -> t.getId().equals(talkId)))
                .sorted(java.util.Comparator
                        .comparingInt(Talk::getDay)
                        .thenComparing(Talk::getStartTime))
                .toList();
    }

    /** Returns the list of talks scheduled in the given scenario ordered by day and time. */
    public List<Talk> findTalksForScenario(String scenarioId) {
        return events.values().stream()
                .flatMap(e -> e.getAgenda().stream())
                .filter(t -> scenarioId.equals(t.getLocation()))
                .sorted(java.util.Comparator
                        .comparingInt(Talk::getDay)
                        .thenComparing(Talk::getStartTime))
                .toList();
    }
}
