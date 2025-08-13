package com.scanales.eventflow.service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.eventflow.model.Speaker;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Tracks simple usage metrics in memory with asynchronous persistence.
 */
@ApplicationScoped
public class UsageMetricsService {

    private static final Logger LOG = Logger.getLogger(UsageMetricsService.class);
    private static final int CURRENT_SCHEMA_VERSION = 2;

    private final Map<String, Long> counters = new ConcurrentHashMap<>();
    private final Map<String, Long> talkViews = new ConcurrentHashMap<>();
    private final Map<String, Long> eventViews = new ConcurrentHashMap<>();
    private final Map<String, Long> stageVisits = new ConcurrentHashMap<>();
    private final Map<String, Long> pageViews = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> rates = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> discardedByReason = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicLong flushFailures = new AtomicLong();
    private final AtomicInteger bufferSize = new AtomicInteger();
    private final AtomicLong writesOk = new AtomicLong();
    private final AtomicLong writesFail = new AtomicLong();
    private volatile long lastFlushTime;
    private volatile String lastError;
    private volatile HealthState currentState = HealthState.OK;
    private volatile boolean bufferWarned;

    private final Path metricsV1Path = Paths.get("data", "metrics-v1.json");
    private final Path metricsV2Path = Paths.get("data", "metrics-v2.json");
    private Path metricsPath;
    private boolean migrateFromV1;
    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private long lastFileSizeBytes;

    @ConfigProperty(name = "metrics.flush-interval", defaultValue = "PT10S")
    Duration flushInterval;

    @ConfigProperty(name = "metrics.dedupe.talk-window", defaultValue = "PT60S")
    Duration talkViewWindow;

    @ConfigProperty(name = "metrics.dedupe.event-window", defaultValue = "PT180S")
    Duration eventViewWindow;

    @ConfigProperty(name = "metrics.dedupe.stage-window", defaultValue = "PT30M")
    Duration stageVisitWindow;

    @ConfigProperty(name = "metrics.dedupe.page-enabled", defaultValue = "false")
    boolean pageViewDedupe;

    @ConfigProperty(name = "metrics.dedupe.page-window", defaultValue = "PT60S")
    Duration pageViewWindow;

    @ConfigProperty(name = "metrics.burst.per-second", defaultValue = "5")
    int burstPerSecond;

    @ConfigProperty(name = "metrics.burst.per-minute", defaultValue = "60")
    int burstPerMinute;

    @ConfigProperty(name = "metrics.buffer.max-size", defaultValue = "10000")
    int bufferMaxSize;

    @Inject
    ObjectMapper mapper;

    @PostConstruct
    void init() {
        load();
        lastFlushTime = System.currentTimeMillis();
        scheduler.scheduleWithFixedDelay(this::flushSafe,
                flushInterval.toMillis(), flushInterval.toMillis(),
                TimeUnit.MILLISECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::flushSafe));
    }

    @PreDestroy
    void shutdown() {
        flushSafe();
        scheduler.shutdown();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        try {
            if (Files.exists(metricsV2Path)) {
                metricsPath = metricsV2Path;
            } else if (Files.exists(metricsV1Path)) {
                metricsPath = metricsV1Path;
                migrateFromV1 = true;
            } else {
                metricsPath = metricsV2Path;
            }
            if (Files.exists(metricsPath)) {
                Map<String, Object> data = mapper.readValue(metricsPath.toFile(), Map.class);
                Object countersObj = data.get("counters");
                if (countersObj instanceof Map<?, ?> m) {
                    m.forEach((k, v) -> counters.put(String.valueOf(k), ((Number) v).longValue()));
                } else if (data.isEmpty() || data.get("meta") == null) {
                    // backward compatibility: whole file is counters map
                    data.forEach((k, v) -> counters.put(String.valueOf(k), ((Number) v).longValue()));
                }
                Object metaObj = data.get("meta");
                if (metaObj instanceof Map<?, ?> meta) {
                    Object disc = meta.get("discarded");
                    if (disc instanceof Map<?, ?> dm) {
                        dm.forEach((k, v) -> discardedByReason
                                .computeIfAbsent(String.valueOf(k), r -> new LongAdder())
                                .add(((Number) v).longValue()));
                    }
                    Object sv = meta.get("schemaVersion");
                    if (sv instanceof Number n) {
                        schemaVersion = n.intValue();
                    }
                    Object fs = meta.get("fileSizeBytes");
                    if (fs instanceof Number n) {
                        lastFileSizeBytes = n.longValue();
                    }
                    Object lf = meta.get("lastFlush");
                    if (lf instanceof Number n) {
                        lastFlushTime = n.longValue();
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to load metrics", e);
        }
    }

    /** Summary information for admin view. */
    public record Summary(long totalKeys, long estimatedSize, Map<String, Long> discarded) {
        public long discardedEvents() {
            return discarded.values().stream().mapToLong(Long::longValue).sum();
        }
    }

    public Summary getSummary() {
        try {
            byte[] json = mapper.writeValueAsBytes(counters);
            return new Summary(counters.size(), json.length, getDiscarded());
        } catch (Exception e) {
            return new Summary(counters.size(), 0L, getDiscarded());
        }
    }

    /** Current schema version loaded in memory. */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** Size in bytes of the last flushed metrics file. */
    public long getFileSizeBytes() {
        return lastFileSizeBytes;
    }

    private boolean isBot(String ua) {
        if (ua == null) return false;
        String u = ua.toLowerCase();
        return u.contains("bot") || u.contains("spider") || u.contains("crawl")
                || u.contains("headless") || u.contains("phantomjs") || u.contains("selenium")
                || u.contains("puppeteer");
    }

    public void recordPageView(String route, String ua) {
        recordPageView(route, null, ua);
    }

    public void recordPageView(String route, String sessionId, String ua) {
        if (isBot(ua)) {
            incrementDiscard("bot");
            return;
        }
        if (sessionId != null && isBurst(sessionId)) {
            incrementDiscard("burst");
            return;
        }
        if (pageViewDedupe && sessionId != null) {
            long now = System.currentTimeMillis();
            String key = sessionId + ":" + route;
            Long last = pageViews.put(key, now);
            if (last != null && now - last < pageViewWindow.toMillis()) {
                incrementDiscard("dedupe");
                return;
            }
        }
        increment("page_view:" + route);
    }

    public void recordEventView(String eventId, String ua) {
        recordEventView(eventId, null, ua);
    }

    public void recordEventView(String eventId, String sessionId, String ua) {
        if (eventId == null) {
            incrementDiscard("invalid");
            return;
        }
        if (isBot(ua)) {
            incrementDiscard("bot");
            return;
        }
        if (sessionId != null && isBurst(sessionId)) {
            incrementDiscard("burst");
            return;
        }
        long now = System.currentTimeMillis();
        if (sessionId != null) {
            String key = sessionId + ":" + eventId;
            Long last = eventViews.put(key, now);
            if (last != null && now - last < eventViewWindow.toMillis()) {
                incrementDiscard("dedupe");
                return;
            }
        }
        increment("event_view:" + eventId);
    }

    public void recordTalkView(String talkId, String sessionId, String ua) {
        if (talkId == null || sessionId == null) {
            incrementDiscard("invalid");
            return;
        }
        if (isBot(ua)) {
            incrementDiscard("bot");
            return;
        }
        if (isBurst(sessionId)) {
            incrementDiscard("burst");
            return;
        }
        long now = System.currentTimeMillis();
        String key = sessionId + ":" + talkId;
        Long last = talkViews.put(key, now);
        if (last != null && now - last < talkViewWindow.toMillis()) {
            incrementDiscard("dedupe");
            return;
        }
        increment("talk_view:" + talkId);
    }

    public void recordTalkRegister(String talkId, java.util.List<Speaker> speakers, String ua) {
        if (talkId == null) {
            incrementDiscard("invalid");
            return;
        }
        if (isBot(ua)) {
            incrementDiscard("bot");
            return;
        }
        if (speakers != null) {
            for (Speaker s : speakers) {
                if (s != null && s.getId() != null) {
                    increment("speaker_popularity:" + s.getId());
                }
            }
        }
        increment("talk_register:" + talkId);
    }

    public void recordStageVisit(String stageId, String timezone, String sessionId, String ua) {
        if (stageId == null || sessionId == null) {
            incrementDiscard("invalid");
            return;
        }
        if (isBot(ua)) {
            incrementDiscard("bot");
            return;
        }
        if (isBurst(sessionId)) {
            incrementDiscard("burst");
            return;
        }
        ZoneId zone = timezone != null ? ZoneId.of(timezone) : ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        String key = sessionId + ":" + stageId + ":" + today;
        long now = System.currentTimeMillis();
        Long last = stageVisits.put(key, now);
        if (last != null && now - last < stageVisitWindow.toMillis()) {
            incrementDiscard("dedupe");
            return;
        }
        increment("stage_visit:" + stageId + ":" + today);
    }

    private void increment(String key) {
        int size = bufferSize.incrementAndGet();
        if (size > bufferMaxSize) {
            bufferSize.decrementAndGet();
            incrementDiscard("buffer_full");
            LOG.warn("discarded_buffer_full");
            updateHealthState();
            return;
        }
        if (!bufferWarned && size >= bufferMaxSize * 0.7) {
            LOG.warn("buffer_threshold_reached");
            bufferWarned = true;
        }
        counters.merge(key, 1L, Long::sum);
        dirty.set(true);
        updateHealthState();
    }

    private void incrementDiscard(String reason) {
        discardedByReason.computeIfAbsent(reason, r -> new LongAdder()).increment();
    }

    private boolean isBurst(String sessionId) {
        if (sessionId == null) return false;
        long now = System.currentTimeMillis();
        RateLimiter rl = rates.computeIfAbsent(sessionId, k -> new RateLimiter());
        synchronized (rl) {
            if (now - rl.secondStart >= 1000) {
                rl.secondStart = now;
                rl.secondCount = 0;
            }
            if (now - rl.minuteStart >= 60000) {
                rl.minuteStart = now;
                rl.minuteCount = 0;
            }
            if (++rl.secondCount > burstPerSecond || ++rl.minuteCount > burstPerMinute) {
                return true;
            }
        }
        return false;
    }

    private void flushSafe() {
        if (!dirty.get()) return;
        LOG.info("metrics_flush_start");
        Map<String, Long> snapshot = Map.copyOf(counters);
        Map<String, Long> discards = getDiscarded();
        Map<String, Object> file = new HashMap<>();
        file.put("counters", snapshot);
        Map<String, Object> meta = new HashMap<>();
        meta.put("schemaVersion", CURRENT_SCHEMA_VERSION);
        meta.put("lastFlush", System.currentTimeMillis());
        meta.put("discarded", discards);
        file.put("meta", meta);
        Path tmp = metricsV2Path.resolveSibling("metrics-v2.json.tmp");
        int attempts = 0;
        long backoff = 50L;
        while (attempts < 3) {
            try {
                Files.createDirectories(metricsV2Path.getParent());
                byte[] json = mapper.writeValueAsBytes(file);
                meta.put("fileSizeBytes", json.length);
                json = mapper.writeValueAsBytes(file);
                Files.write(tmp, json);
                Files.move(tmp, metricsV2Path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                metricsPath = metricsV2Path;
                schemaVersion = CURRENT_SCHEMA_VERSION;
                lastFileSizeBytes = json.length;
                lastFlushTime = System.currentTimeMillis();
                bufferSize.set(0);
                bufferWarned = false;
                writesOk.incrementAndGet();
                lastError = null;
                if (migrateFromV1) {
                    try { Files.deleteIfExists(metricsV1Path); } catch (IOException ignored) {}
                    migrateFromV1 = false;
                }
                dirty.set(false);
                LOG.info("metrics_flush_ok");
                updateHealthState();
                return;
            } catch (Exception e) {
                attempts++;
                lastError = e.getMessage();
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                backoff *= 2;
            }
        }
        LOG.error("metrics_flush_fail");
        increment("discarded_events");
        flushFailures.incrementAndGet();
        incrementDiscard("invalid");
        writesFail.incrementAndGet();
        updateHealthState();
    }

    /** Returns a snapshot of all counters for read-only purposes. */
    public Map<String, Long> snapshot() {
        return Map.copyOf(counters);
    }

    public Map<String, Long> getDiscarded() {
        Map<String, Long> snap = new HashMap<>();
        discardedByReason.forEach((k, v) -> snap.put(k, v.longValue()));
        snap.put("invalid", snap.getOrDefault("invalid", 0L));
        snap.put("bot", snap.getOrDefault("bot", 0L));
        snap.put("burst", snap.getOrDefault("burst", 0L));
        snap.put("dedupe", snap.getOrDefault("dedupe", 0L));
        snap.put("buffer_full", snap.getOrDefault("buffer_full", 0L));
        return snap;
    }

    public record Config(long talkViewWindowSeconds, long eventViewWindowSeconds,
            long stageVisitWindowSeconds, boolean pageViewDedupe,
            int burstPerSecond, int burstPerMinute) {
        public long stageVisitWindowMinutes() {
            return stageVisitWindowSeconds / 60;
        }
    }

    public Config getConfig() {
        return new Config(talkViewWindow.getSeconds(), eventViewWindow.getSeconds(),
                stageVisitWindow.getSeconds(), pageViewDedupe, burstPerSecond, burstPerMinute);
    }

    /** Returns the last modification time of the metrics file or {@code 0} if unavailable. */
    public long getLastUpdatedMillis() {
        try {
            if (Files.exists(metricsPath)) {
                return Files.getLastModifiedTime(metricsPath).toMillis();
            }
        } catch (IOException e) {
            LOG.debug("Failed to read metrics timestamp", e);
        }
        return 0L;
    }

    public record Health(HealthState estado, long lastFlush, long flushIntervalMillis,
            int bufferCurrentSize, int bufferMaxSize, long writesOk, long writesFail,
            String lastError, long fileSizeBytes, Map<String, Long> discards) {}

    public enum HealthState { OK, DEGRADADO, ERROR }

    public Health getHealth() {
        return new Health(currentState, lastFlushTime, flushInterval.toMillis(),
                bufferSize.get(), bufferMaxSize, writesOk.get(), writesFail.get(),
                lastError, lastFileSizeBytes, getDiscarded());
    }

    private HealthState computeState() {
        long now = System.currentTimeMillis();
        long age = now - lastFlushTime;
        long interval = flushInterval.toMillis();
        int buf = bufferSize.get();
        long fail = writesFail.get();
        if (age >= interval * 5 || buf >= bufferMaxSize || fail >= 3) {
            return HealthState.ERROR;
        }
        if (age >= interval * 2 || buf >= (int) (bufferMaxSize * 0.7) || fail >= 1) {
            return HealthState.DEGRADADO;
        }
        return HealthState.OK;
    }

    private void updateHealthState() {
        HealthState newState = computeState();
        if (newState != currentState) {
            LOG.warnf("health_state_change %s->%s", currentState, newState);
            currentState = newState;
        }
    }

    /** Test helper to reset internal state between tests. */
    void reset() {
        counters.clear();
        talkViews.clear();
        eventViews.clear();
        stageVisits.clear();
        pageViews.clear();
        rates.clear();
        discardedByReason.clear();
        bufferSize.set(0);
        bufferWarned = false;
        dirty.set(false);
        writesOk.set(0);
        writesFail.set(0);
        flushFailures.set(0);
        lastFlushTime = System.currentTimeMillis();
        lastError = null;
        currentState = HealthState.OK;
    }

    private static class RateLimiter {
        long secondStart = System.currentTimeMillis();
        int secondCount = 0;
        long minuteStart = System.currentTimeMillis();
        int minuteCount = 0;
    }
}

