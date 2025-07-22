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

    private final Map<String, Event> events = new ConcurrentHashMap<>();

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
}
