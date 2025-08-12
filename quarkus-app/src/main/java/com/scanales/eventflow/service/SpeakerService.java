package com.scanales.eventflow.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

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

    private static final Logger LOG = Logger.getLogger(SpeakerService.class);

    @PostConstruct
    void init() {
        speakers.putAll(persistence.loadSpeakers());
        LOG.infof("Loaded %d speakers into memory", speakers.size());
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
        // ensure the main speaker is present and listed first
        List<Speaker> allSpeakers = new ArrayList<>();
        allSpeakers.add(sp);
        Set<String> newIds = new HashSet<>();
        newIds.add(sp.getId());
        if (talk.getSpeakers() != null) {
            for (Speaker other : talk.getSpeakers()) {
                if (other != null && !sp.getId().equals(other.getId())) {
                    Speaker co = speakers.get(other.getId());
                    if (co != null) {
                        allSpeakers.add(co);
                        newIds.add(co.getId());
                    }
                }
            }
        }
        talk.setSpeakers(allSpeakers);
        sp.getTalks().removeIf(t -> t.getId().equals(talk.getId()));
        sp.getTalks().add(talk);
        // ensure co-speakers have the talk and remove from those no longer associated
        for (Speaker s : speakers.values()) {
            if (!newIds.contains(s.getId())) {
                s.getTalks().removeIf(t -> t.getId().equals(talk.getId()));
            }
        }
        for (int i = 1; i < allSpeakers.size(); i++) {
            Speaker co = allSpeakers.get(i);
            co.getTalks().removeIf(t -> t.getId().equals(talk.getId()));
            co.getTalks().add(talk);
        }
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
        for (Speaker s : speakers.values()) {
            s.getTalks().removeIf(t -> t.getId().equals(talkId));
        }
        for (Event e : eventService.listEvents()) {
            e.getAgenda().removeIf(t -> t.getId().equals(talkId));
        }
        persistence.saveSpeakers(new ConcurrentHashMap<>(speakers));
    }

    /** Reloads speakers from persistence replacing the current cache. */
    public void reload() {
        speakers.clear();
        speakers.putAll(persistence.loadSpeakers());
    }
}
