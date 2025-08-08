package com.scanales.eventflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.model.Event;

/**
 * Simple in-memory service for managing speakers globally.
 */
@ApplicationScoped
public class SpeakerService {

    private static final Map<String, Speaker> speakers = new ConcurrentHashMap<>();

    @Inject
    EventService eventService;

    @Inject
    PersistenceService persistence;

    @PostConstruct
    void init() {
        speakers.putAll(persistence.loadSpeakers());
    }

    public List<Speaker> listSpeakers() {
        return new ArrayList<>(speakers.values());
    }

    public Speaker getSpeaker(String id) {
        return speakers.get(id);
    }

    public void saveSpeaker(Speaker speaker) {
        if (speaker == null || speaker.getId() == null) {
            return;
        }
        speakers.compute(speaker.getId(), (id, existing) -> {
            if (existing == null) {
                return speaker;
            }
            existing.setName(speaker.getName());
            existing.setBio(speaker.getBio());
            existing.setPhotoUrl(speaker.getPhotoUrl());
            existing.setWebsite(speaker.getWebsite());
            existing.setTwitter(speaker.getTwitter());
            existing.setLinkedin(speaker.getLinkedin());
            existing.setInstagram(speaker.getInstagram());
            if (speaker.getTalks() != null && !speaker.getTalks().isEmpty()) {
                existing.setTalks(speaker.getTalks());
            }
            return existing;
        });
        persistence.saveSpeakers(new ConcurrentHashMap<>(speakers));
    }

    public void deleteSpeaker(String id) {
        Speaker sp = speakers.remove(id);
        if (sp != null) {
            // remove talks from events as well
            for (Talk t : sp.getTalks()) {
                for (Event e : eventService.listEvents()) {
                    e.getAgenda().removeIf(tt -> tt.getId().equals(t.getId()));
                }
            }
        }
        persistence.saveSpeakers(new ConcurrentHashMap<>(speakers));
    }

    public void saveTalk(String speakerId, Talk talk) {
        Speaker sp = speakers.get(speakerId);
        if (sp == null || talk == null || talk.getId() == null) {
            return;
        }
        // ensure the main speaker is present in the talk
        if (talk.getSpeakers() == null || talk.getSpeakers().isEmpty()) {
            talk.setSpeakers(List.of(sp));
        }
        sp.getTalks().removeIf(t -> t.getId().equals(talk.getId()));
        sp.getTalks().add(talk);
        // propagate updates to all events using this talk
        for (Talk t : eventService.findTalkOccurrences(talk.getId())) {
            t.setName(talk.getName());
            t.setDescription(talk.getDescription());
            t.setDurationMinutes(talk.getDurationMinutes());
            t.setSpeakers(talk.getSpeakers());
        }
        persistence.saveSpeakers(new ConcurrentHashMap<>(speakers));
    }

    public Talk getTalk(String speakerId, String talkId) {
        Speaker sp = speakers.get(speakerId);
        if (sp == null) {
            return null;
        }
        return sp.getTalks().stream()
                .filter(t -> t.getId().equals(talkId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds a talk by id across all speakers.
     */
    public Talk findTalk(String talkId) {
        return speakers.values().stream()
                .flatMap(s -> s.getTalks().stream())
                .filter(t -> t.getId().equals(talkId))
                .findFirst()
                .orElse(null);
    }

    public void deleteTalk(String speakerId, String talkId) {
        Speaker sp = speakers.get(speakerId);
        if (sp != null) {
            sp.getTalks().removeIf(t -> t.getId().equals(talkId));
        }
        for (Event e : eventService.listEvents()) {
            e.getAgenda().removeIf(t -> t.getId().equals(talkId));
        }
        persistence.saveSpeakers(new ConcurrentHashMap<>(speakers));
    }
}
