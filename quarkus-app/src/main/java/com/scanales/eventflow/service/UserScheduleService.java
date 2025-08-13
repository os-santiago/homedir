package com.scanales.eventflow.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** In-memory store for user schedules and talk details. */
@ApplicationScoped
public class UserScheduleService {

    private static final Logger LOG = Logger.getLogger(UserScheduleService.class);

    /** Stores user email -> (talkId -> details). */
    private final Map<String, Map<String, TalkDetails>> schedules = new ConcurrentHashMap<>();

    /** Loaded historical schedules per year. */
    private final Map<Integer, Map<String, Map<String, TalkDetails>>> historical = new ConcurrentHashMap<>();

    @ConfigProperty(name = "read.window", defaultValue = "PT2S")
    Duration readWindow;

    @ConfigProperty(name = "read.max-stale", defaultValue = "PT10S")
    Duration maxStale;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private volatile Future<?> refreshTask;
    private final AtomicInteger windowReads = new AtomicInteger();
    private volatile long lastRefreshDurationMs;
    private final List<Long> refreshDurations = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong readsMemory = new AtomicLong();
    private final AtomicLong refreshCount = new AtomicLong();
    private final AtomicLong refreshCoalesced = new AtomicLong();
    private final AtomicLong staleServed = new AtomicLong();

    @Inject
    PersistenceService persistence;

    @Inject
    CapacityService capacity;

    private int activeYear;

    @PostConstruct
    void init() {
        activeYear = persistence.findLatestUserScheduleYear();
        schedules.putAll(persistence.loadUserSchedules(activeYear));
        long secs = readWindow.getSeconds();
        if (secs < 1) readWindow = Duration.ofSeconds(1);
        if (secs > 2) readWindow = Duration.ofSeconds(2);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
    }

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
        int currentYear = Year.now().getValue();
        if (currentYear != activeYear) {
            schedules.clear();
            activeYear = currentYear;
        }
        boolean added = schedules.computeIfAbsent(email, k -> new ConcurrentHashMap<>())
                .putIfAbsent(talkId, new TalkDetails()) == null;
        LOG.infov("addTalk(user={0}, talk={1}, result={2})", email, talkId,
                added ? "added" : "exists");
        if (added) {
            persistence.saveUserSchedules(activeYear, schedules);
        }
        return added;
    }

    /** Returns the set of talk ids registered by the user. */
    public Set<String> getTalksForUser(String email) {
        recordRead();
        if (email == null) {
            return java.util.Set.of();
        }
        Map<String, TalkDetails> talks = schedules.get(email);
        return talks == null ? java.util.Set.of() : talks.keySet();
    }

    /** Returns the map of talk details for the user. */
    public Map<String, TalkDetails> getTalkDetailsForUser(String email) {
        recordRead();
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
        persistence.saveUserSchedules(activeYear, schedules);
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
            LOG.infov("removeTalk(user={0}, talk={1}, result={2})", email, talkId,
                    removed ? "removed" : "not-found");
            if (removed) {
                persistence.saveUserSchedules(activeYear, schedules);
            }
            return removed;
        }
        LOG.infov("removeTalk(user={0}, talk={1}, result=not-found)", email, talkId);
        return false;
    }

    /** Summary counts for a user's talks. */
    public record Summary(int total, long attended, long rated) {}

    public Summary getSummaryForUser(String email) {
        recordRead();
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

    /** Reloads schedules from persistent storage. */
    public void reload() {
        schedules.clear();
        init();
    }

    /** Result codes for loading historical data. */
    public enum LoadStatus { LOADED, NO_DATA, CAPACITY, ERROR }

    /** Loads schedules for the given past year if capacity permits. */
    public LoadStatus loadHistorical(int year) {
        if (historical.containsKey(year)) {
            return LoadStatus.LOADED;
        }
        CapacityService.Status st = capacity.evaluate();
        if (st.mode() == CapacityService.Mode.CONTAINING) {
            return LoadStatus.CAPACITY;
        }
        try {
            Map<String, Map<String, TalkDetails>> data = persistence.loadUserSchedules(year);
            if (data.isEmpty()) {
                return LoadStatus.NO_DATA;
            }
            historical.put(year, data);
            return LoadStatus.LOADED;
        } catch (Exception e) {
            LOG.error("Failed to load historical year " + year, e);
            return LoadStatus.ERROR;
        }
    }

    /** Returns talk details for the given user and year previously loaded via {@link #loadHistorical}. */
    public Map<String, TalkDetails> getHistoricalTalkDetailsForUser(int year, String email) {
        if (email == null) {
            return java.util.Map.of();
        }
        Map<String, Map<String, TalkDetails>> yearData = historical.get(year);
        if (yearData == null) {
            return java.util.Map.of();
        }
        return yearData.getOrDefault(email, java.util.Map.of());
    }

    /** Unloads previously loaded historical data for the given year. */
    public void unloadHistorical(int year) {
        historical.remove(year);
    }

    /** Returns the set of years for which schedule files exist. */
    public Set<Integer> getAvailableYears() {
        recordRead();
        return persistence.listUserScheduleYears();
    }

    private void recordRead() {
        readsMemory.incrementAndGet();
        if (refreshInProgress.get()) {
            staleServed.incrementAndGet();
        }
        if (refreshTask == null) {
            windowReads.set(1);
            refreshTask = scheduler.schedule(this::refreshSnapshot,
                    readWindow.toMillis(), TimeUnit.MILLISECONDS);
        } else {
            windowReads.incrementAndGet();
        }
    }

    private void refreshSnapshot() {
        refreshInProgress.set(true);
        long reads = windowReads.getAndSet(0);
        refreshCoalesced.addAndGet(reads);
        long start = System.currentTimeMillis();
        try {
            Map<String, Map<String, TalkDetails>> data =
                    persistence.loadUserSchedules(activeYear);
            schedules.clear();
            schedules.putAll(data);
        } catch (Exception e) {
            LOG.warn("refresh_failed", e);
        } finally {
            lastRefreshDurationMs = System.currentTimeMillis() - start;
            synchronized (refreshDurations) {
                refreshDurations.add(lastRefreshDurationMs);
                if (refreshDurations.size() > 100) {
                    refreshDurations.remove(0);
                }
            }
            refreshCount.incrementAndGet();
            refreshInProgress.set(false);
            refreshTask = null;
        }
    }

    public record ReadMetrics(long memory, long refreshCount, long refreshCoalesced,
            long refreshLastDurationMs, long refreshP95Ms, long staleServed) {}

    public ReadMetrics getReadMetrics() {
        long p95 = 0L;
        List<Long> copy;
        synchronized (refreshDurations) {
            copy = new ArrayList<>(refreshDurations);
        }
        if (!copy.isEmpty()) {
            Collections.sort(copy);
            int index = (int) Math.ceil(copy.size() * 0.95) - 1;
            if (index < 0) {
                index = 0;
            }
            p95 = copy.get(index);
        }
        return new ReadMetrics(readsMemory.get(), refreshCount.get(),
                refreshCoalesced.get(), lastRefreshDurationMs, p95,
                staleServed.get());
    }
}
