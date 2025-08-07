package com.scanales.eventflow.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory store for user schedules and talk details. */
@ApplicationScoped
public class UserScheduleService {

    /** Stores user email -> (talkId -> details). */
    private final Map<String, Map<String, TalkDetails>> schedules = new ConcurrentHashMap<>();

    /** Details tracked for each talk registered by a user. */
    public static class TalkDetails {
        public Set<String> motivations = ConcurrentHashMap.newKeySet();
        public boolean attended;
        public Integer rating; // 1-5 or null if not rated
    }

    /** Adds the talk id to the schedule for the given user email. */
    public boolean addTalkForUser(String email, String talkId) {
        if (email == null || talkId == null) {
            return false;
        }
        return schedules.computeIfAbsent(email, k -> new ConcurrentHashMap<>())
                .putIfAbsent(talkId, new TalkDetails()) == null;
    }

    /** Returns the set of talk ids registered by the user. */
    public Set<String> getTalksForUser(String email) {
        if (email == null) {
            return java.util.Set.of();
        }
        Map<String, TalkDetails> talks = schedules.get(email);
        return talks == null ? java.util.Set.of() : talks.keySet();
    }

    /** Returns the map of talk details for the user. */
    public Map<String, TalkDetails> getTalkDetailsForUser(String email) {
        if (email == null) {
            return java.util.Map.of();
        }
        return schedules.getOrDefault(email, java.util.Map.of());
    }

    /** Updates the stored details for a given talk. */
    public boolean updateTalk(String email, String talkId, Boolean attended, Integer rating, Set<String> motivations) {
        if (email == null || talkId == null) return false;
        Map<String, TalkDetails> talks = schedules.get(email);
        if (talks == null) return false;
        TalkDetails details = talks.get(talkId);
        if (details == null) return false;
        if (attended != null) {
            details.attended = attended;
        }
        if (rating != null) {
            details.rating = rating;
        }
        if (motivations != null) {
            details.motivations.clear();
            details.motivations.addAll(motivations);
        }
        return true;
    }

    /** Removes the talk id from the user schedule. */
    public boolean removeTalkForUser(String email, String talkId) {
        if (email == null || talkId == null) return false;
        Map<String, TalkDetails> talks = schedules.get(email);
        if (talks != null) {
            boolean removed = talks.remove(talkId) != null;
            if (talks.isEmpty()) {
                schedules.remove(email);
            }
            return removed;
        }
        return false;
    }

    /** Summary counts for a user's talks. */
    public record Summary(int total, long attended, long rated) {}

    public Summary getSummaryForUser(String email) {
        if (email == null) {
            return new Summary(0, 0, 0);
        }
        Map<String, TalkDetails> talks = schedules.get(email);
        if (talks == null) {
            return new Summary(0, 0, 0);
        }
        long attendedCount = talks.values().stream().filter(t -> t.attended).count();
        long ratedCount = talks.values().stream().filter(t -> t.rating != null).count();
        return new Summary(talks.size(), attendedCount, ratedCount);
    }
}
