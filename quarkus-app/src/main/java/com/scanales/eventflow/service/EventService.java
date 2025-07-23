package com.scanales.eventflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Talk;

/** Simple in-memory store for events. */
@ApplicationScoped
public class EventService {

    /**
     * Global cache of events shared by all sessions. Using a static map ensures
     * the same {@code Event} instance is returned for a given id, reducing
     * memory usage and avoiding unnecessary object duplication. The
     * ConcurrentHashMap provides lock-free reads and efficient updates for a
     * high performance setup.
     */
    private static final Map<String, Event> events = new ConcurrentHashMap<>();

    public List<Event> listEvents() {
        return new ArrayList<>(events.values());
    }

    public Event getEvent(String id) {
        return events.get(id);
    }

    public void saveEvent(Event event) {
        events.put(event.getId(), event);
    }

    public void deleteEvent(String id) {
        events.remove(id);
    }

    public void saveScenario(String eventId, Scenario scenario) {
        Event event = events.get(eventId);
        if (event == null) {
            return;
        }
        event.getScenarios().removeIf(s -> s.getId().equals(scenario.getId()));
        event.getScenarios().add(scenario);
    }

    public void deleteScenario(String eventId, String scenarioId) {
        Event event = events.get(eventId);
        if (event != null) {
            event.getScenarios().removeIf(s -> s.getId().equals(scenarioId));
        }
    }

    public void saveTalk(String eventId, Talk talk) {
        Event event = events.get(eventId);
        if (event == null) {
            return;
        }
        event.getAgenda().removeIf(t -> t.getId().equals(talk.getId()));
        event.getAgenda().add(talk);
    }

    public void deleteTalk(String eventId, String talkId) {
        Event event = events.get(eventId);
        if (event != null) {
            event.getAgenda().removeIf(t -> t.getId().equals(talkId));
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
