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
    private final Path metricsPath = Paths.get("data", "metrics-v1.json");

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

    @Inject
    ObjectMapper mapper;

    @PostConstruct
    void init() {
        load();
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
        counters.merge(key, 1L, Long::sum);
        dirty.set(true);
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
        Map<String, Long> snapshot = Map.copyOf(counters);
        Map<String, Long> discards = getDiscarded();
        Map<String, Object> file = new HashMap<>();
        file.put("counters", snapshot);
        Map<String, Object> meta = new HashMap<>();
        meta.put("schemaVersion", 3);
        meta.put("lastFlush", System.currentTimeMillis());
        meta.put("discarded", discards);
        file.put("meta", meta);
        Path tmp = metricsPath.resolveSibling("metrics-v1.json.tmp");
        int attempts = 0;
        long backoff = 50L;
        while (attempts < 3) {
            try {
                Files.createDirectories(metricsPath.getParent());
                mapper.writeValue(tmp.toFile(), file);
                Files.move(tmp, metricsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                dirty.set(false);
                return;
            } catch (Exception e) {
                attempts++;
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                backoff *= 2;
            }
        }
        increment("discarded_events");
        flushFailures.incrementAndGet();
        incrementDiscard("invalid");
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
        return snap;
    }

    public record Config(long talkViewWindowSeconds, long eventViewWindowSeconds,
            long stageVisitWindowSeconds, boolean pageViewDedupe,
            int burstPerSecond, int burstPerMinute) {}

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

    private static class RateLimiter {
        long secondStart = System.currentTimeMillis();
        int secondCount = 0;
        long minuteStart = System.currentTimeMillis();
        int minuteCount = 0;
    }
}

