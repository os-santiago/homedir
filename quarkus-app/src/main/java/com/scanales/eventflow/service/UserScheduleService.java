package com.scanales.eventflow.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Simple in-memory store mapping user emails to talk ids. */
@ApplicationScoped
public class UserScheduleService {

    private final Map<String, Set<String>> schedules = new ConcurrentHashMap<>();

    /** Adds the talk id to the schedule for the given user email. */
    public void addTalkForUser(String email, String talkId) {
        schedules.computeIfAbsent(email, k -> ConcurrentHashMap.newKeySet())
                .add(talkId);
    }

    /** Returns the set of talk ids registered by the user. */
    public Set<String> getTalksForUser(String email) {
        return schedules.getOrDefault(email, java.util.Set.of());
    }

    /** Removes the talk id from the user schedule. */
    public void removeTalkForUser(String email, String talkId) {
        Set<String> talks = schedules.get(email);
        if (talks != null) {
            talks.remove(talkId);
            if (talks.isEmpty()) {
                schedules.remove(email);
            }
        }
    }
}
