package com.scanales.eventflow.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import jakarta.inject.Inject;

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

    @Inject
    ObjectMapper objectMapper;

    private ObjectMapper mapper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Path dataDir = Paths.get(System.getProperty("eventflow.data.dir", "data"));
    private final Path eventsFile = dataDir.resolve("events.json");
    private final Path speakersFile = dataDir.resolve("speakers.json");
    private static final String SCHEDULE_FILE_PREFIX = "user-schedule-";
    private static final String SCHEDULE_FILE_SUFFIX = ".json";

    private volatile boolean lowDiskSpace;
    private static final long MIN_FREE_BYTES = 50L * 1024 * 1024; // 50 MB

    @PostConstruct
    void init() {
        mapper = objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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

    /** Persists user schedules for the given year asynchronously. */
    public void saveUserSchedules(int year, Map<String, Map<String, UserScheduleService.TalkDetails>> schedules) {
        scheduleWrite(scheduleFile(year), schedules);
    }

    /** Loads events from disk or returns an empty map if none. */
    public Map<String, Event> loadEvents() {
        return read(eventsFile, new TypeReference<Map<String, Event>>() {});
    }

    /** Loads speakers from disk or returns an empty map if none. */
    public Map<String, Speaker> loadSpeakers() {
        return read(speakersFile, new TypeReference<Map<String, Speaker>>() {});
    }

    /** Loads user schedules for the given year from disk or returns an empty map if none. */
    public Map<String, Map<String, UserScheduleService.TalkDetails>> loadUserSchedules(int year) {
        return read(scheduleFile(year), new TypeReference<Map<String, Map<String, UserScheduleService.TalkDetails>>>() {});
    }

    /** Returns the most recent user schedule year within the last year or the current year if none. */
    public int findLatestUserScheduleYear() {
        int currentYear = java.time.Year.now().getValue();
        try (var stream = Files.list(dataDir)) {
            var years = stream.map(Path::getFileName)
                    .map(Path::toString)
                    .filter(n -> n.startsWith(SCHEDULE_FILE_PREFIX) && n.endsWith(SCHEDULE_FILE_SUFFIX))
                    .map(n -> n.substring(SCHEDULE_FILE_PREFIX.length(), n.length() - SCHEDULE_FILE_SUFFIX.length()))
                    .map(s -> {
                        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
                    })
                    .filter(y -> y >= currentYear - 1)
                    .collect(Collectors.toList());
            return years.isEmpty() ? currentYear : years.stream().mapToInt(Integer::intValue).max().orElse(currentYear);
        } catch (IOException e) {
            LOG.error("Failed to scan data directory", e);
            return currentYear;
        }
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
            int attempts = 0;
            boolean success = false;
            while (attempts < 3 && !success) {
                attempts++;
                try {
                    Path tmp = Files.createTempFile(dataDir, file.getFileName().toString(), ".tmp");
                    try {
                        mapper.writeValue(tmp.toFile(), data);
                        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        LOG.infof("Persisted %s at %s", file.getFileName(), java.time.Instant.now());
                        success = true;
                    } finally {
                        try {
                            Files.deleteIfExists(tmp);
                        } catch (IOException ignore) {
                            // ignore cleanup errors
                        }
                    }
                } catch (IOException e) {
                    if (attempts >= 3) {
                        LOG.error("Failed to persist data to " + file, e);
                    } else {
                        LOG.warn("Persistence attempt " + attempts + " failed for " + file + ", retrying");
                        try { Thread.sleep(250); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                    }
                }
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

    private Path scheduleFile(int year) {
        return dataDir.resolve(SCHEDULE_FILE_PREFIX + year + SCHEDULE_FILE_SUFFIX);
    }
}
