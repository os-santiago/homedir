package com.scanales.eventflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import com.scanales.eventflow.model.Speaker;

/**
 * Simple in-memory service for managing speakers globally.
 */
@ApplicationScoped
public class SpeakerService {

    private static final Map<String, Speaker> speakers = new ConcurrentHashMap<>();

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
            return existing;
        });
    }

    public void deleteSpeaker(String id) {
        speakers.remove(id);
    }
}
