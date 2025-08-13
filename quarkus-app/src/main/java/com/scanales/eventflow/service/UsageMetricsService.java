package com.scanales.eventflow.service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicLong discarded = new AtomicLong();
    private final Path metricsPath = Paths.get("data", "metrics-v1.json");

    @ConfigProperty(name = "metrics.flush-interval", defaultValue = "PT10S")
    Duration flushInterval;

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

    private void load() {
        try {
            if (Files.exists(metricsPath)) {
                Map<String, Long> data = mapper.readValue(metricsPath.toFile(),
                        mapper.getTypeFactory().constructMapType(Map.class, String.class, Long.class));
                counters.putAll(data);
            }
        } catch (Exception e) {
            LOG.warn("Failed to load metrics", e);
        }
    }

    /** Summary information for admin view. */
    public record Summary(long totalKeys, long estimatedSize, long discardedEvents) {}

    public Summary getSummary() {
        try {
            byte[] json = mapper.writeValueAsBytes(counters);
            return new Summary(counters.size(), json.length, discarded.get());
        } catch (Exception e) {
            return new Summary(counters.size(), 0L, discarded.get());
        }
    }

    private boolean isBot(String ua) {
        if (ua == null) return false;
        String u = ua.toLowerCase();
        return u.contains("bot") || u.contains("spider") || u.contains("crawl");
    }

    public void recordPageView(String route, String ua) {
        if (isBot(ua)) return;
        increment("page_view:" + route);
    }

    public void recordEventView(String eventId, String ua) {
        if (isBot(ua)) return;
        increment("event_view:" + eventId);
    }

    public void recordTalkView(String talkId, String sessionId, String ua) {
        if (isBot(ua) || sessionId == null) return;
        long now = System.currentTimeMillis();
        String key = sessionId + ":" + talkId;
        Long last = talkViews.put(key, now);
        if (last == null || now - last > 3000) {
            increment("talk_view:" + talkId);
        }
    }

    public void recordTalkRegister(String talkId, java.util.List<Speaker> speakers, String ua) {
        if (isBot(ua)) return;
        increment("talk_register:" + talkId);
        if (speakers != null) {
            for (Speaker s : speakers) {
                if (s != null && s.getId() != null) {
                    increment("speaker_popularity:" + s.getId());
                }
            }
        }
    }

    public void recordStageVisit(String stageId, String timezone, String ua) {
        if (isBot(ua) || stageId == null) return;
        ZoneId zone = timezone != null ? ZoneId.of(timezone) : ZoneId.systemDefault();
        String key = "stage_visit:" + stageId + ":" + LocalDate.now(zone);
        increment(key);
    }

    private void increment(String key) {
        counters.merge(key, 1L, Long::sum);
        dirty.set(true);
    }

    private void flushSafe() {
        if (!dirty.get()) return;
        Map<String, Long> snapshot = Map.copyOf(counters);
        Path tmp = metricsPath.resolveSibling("metrics-v1.json.tmp");
        int attempts = 0;
        long backoff = 50L;
        while (attempts < 3) {
            try {
                Files.createDirectories(metricsPath.getParent());
                mapper.writeValue(tmp.toFile(), snapshot);
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
        discarded.incrementAndGet();
    }

    /** Returns a snapshot of all counters for read-only purposes. */
    public Map<String, Long> snapshot() {
        return Map.copyOf(counters);
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
}

