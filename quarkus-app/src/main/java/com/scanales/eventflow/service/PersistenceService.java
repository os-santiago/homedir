package com.scanales.eventflow.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Speaker;

/**
 * Persists application data to JSON files using an asynchronous writer. All
 * write operations are queued on a single thread to avoid blocking user
 * interactions.
 */
@ApplicationScoped
public class PersistenceService {

    private static final Logger LOG = Logger.getLogger(PersistenceService.class);

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Path dataDir = Paths.get(System.getProperty("eventflow.data.dir", "data"));
    private final Path eventsFile = dataDir.resolve("events.json");
    private final Path speakersFile = dataDir.resolve("speakers.json");

    private volatile boolean lowDiskSpace;
    private static final long MIN_FREE_BYTES = 50L * 1024 * 1024; // 50 MB

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(dataDir);
            LOG.infov("Using data directory {0}", dataDir.toAbsolutePath());
            try (var stream = Files.list(dataDir)) {
                if (stream.findAny().isPresent()) {
                    LOG.info("Persistence data found");
                } else {
                    LOG.info("Data directory is empty");
                }
            }
        } catch (IOException e) {
            LOG.error("Unable to initialize data directory", e);
        }
    }

    /** Returns whether the storage has critically low free space. */
    public boolean isLowDiskSpace() {
        return lowDiskSpace;
    }

    /** Persists all events asynchronously. */
    public void saveEvents(Map<String, Event> events) {
        scheduleWrite(eventsFile, events);
    }

    /** Persists all speakers asynchronously. */
    public void saveSpeakers(Map<String, Speaker> speakers) {
        scheduleWrite(speakersFile, speakers);
    }

    /** Loads events from disk or returns an empty map if none. */
    public Map<String, Event> loadEvents() {
        return read(eventsFile, new TypeReference<Map<String, Event>>() {});
    }

    /** Loads speakers from disk or returns an empty map if none. */
    public Map<String, Speaker> loadSpeakers() {
        return read(speakersFile, new TypeReference<Map<String, Speaker>>() {});
    }

    /** Percentage of disk usage for the data directory (0-100). */
    public double getDiskUsage() {
        File f = dataDir.toFile();
        long total = f.getTotalSpace();
        long free = f.getUsableSpace();
        if (total == 0) {
            return 0d;
        }
        return ((double) (total - free) / (double) total);
    }

    private void scheduleWrite(Path file, Object data) {
        executor.submit(() -> {
            checkDiskSpace();
            if (lowDiskSpace) {
                LOG.warn("Low disk space - skipping persistence");
                return;
            }
            try {
                mapper.writeValue(file.toFile(), data);
                LOG.infof("Persisted %s at %s", file.getFileName(), java.time.Instant.now());
            } catch (IOException e) {
                LOG.error("Failed to persist data to " + file, e);
            }
        });
    }

    private <T> Map<String, T> read(Path file, TypeReference<Map<String, T>> type) {
        if (!Files.exists(file)) {
            LOG.infof("No persistence file %s found - starting empty", file.toAbsolutePath());
            return new ConcurrentHashMap<>();
        }
        try {
            Map<String, T> data = mapper.readValue(file.toFile(), type);
            LOG.infof("Loaded %d entries from %s", data.size(), file.toAbsolutePath());
            return new ConcurrentHashMap<>(data);
        } catch (IOException e) {
            LOG.error("Failed to read " + file.toAbsolutePath(), e);
            return new ConcurrentHashMap<>();
        }
    }

    private void checkDiskSpace() {
        File f = dataDir.toFile();
        long free = f.getUsableSpace();
        lowDiskSpace = free < MIN_FREE_BYTES;
    }

    /** Blocks until all queued persistence tasks are finished. */
    public void flush() {
        try {
            Future<?> f = executor.submit(() -> {});
            f.get();
        } catch (Exception e) {
            // ignore
        }
    }
}
