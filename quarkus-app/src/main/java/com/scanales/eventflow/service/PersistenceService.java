package com.scanales.eventflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.model.UserProfile;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Persists application data to JSON files using an asynchronous writer. All
 * write operations are
 * queued on a single thread to avoid blocking user interactions.
 */
@ApplicationScoped
public class PersistenceService {

  private static final Logger LOG = Logger.getLogger(PersistenceService.class);

  @Inject
  ObjectMapper objectMapper;

  private ObjectMapper mapper;
  private BlockingQueue<Runnable> queue;
  private ThreadPoolExecutor executor;
  private ScheduledExecutorService retryScheduler;

  @ConfigProperty(name = "persist.queue.max", defaultValue = "10000")
  int queueMax = 10000;

  private final AtomicLong writesOk = new AtomicLong();
  private final AtomicLong writesFail = new AtomicLong();
  private final AtomicLong writesRetries = new AtomicLong();
  private final AtomicLong queueDropped = new AtomicLong();
  private volatile String lastError;

  private final Path dataDir = Paths.get(System.getProperty("homedir.data.dir", "data"));
  private final Path eventsFile = dataDir.resolve("events.json");
  private final Path speakersFile = dataDir.resolve("speakers.json");
  private final Path profilesFile = dataDir.resolve("user-profiles.json");
  private static final String SCHEDULE_FILE_PREFIX = "user-schedule-";
  private static final String SCHEDULE_FILE_SUFFIX = ".json";

  private volatile boolean lowDiskSpace;
  private static final long MIN_FREE_BYTES = 50L * 1024 * 1024; // 50 MB

  @PostConstruct
  void init() {
    mapper = objectMapper
        .copy()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new JavaTimeModule());
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
    if (queueMax <= 0) {
      queueMax = 10000;
    }
    queue = new ArrayBlockingQueue<>(queueMax);
    executor = new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        queue,
        r -> {
          Thread t = new Thread(r, "persistence-writer");
          t.setDaemon(true);
          return t;
        });
    retryScheduler = Executors.newSingleThreadScheduledExecutor(
        r -> {
          Thread t = new Thread(r, "persistence-retry");
          t.setDaemon(true);
          return t;
        });
  }

  @PreDestroy
  void shutdown() {
    retryScheduler.shutdown();
    try {
      if (!retryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        LOG.warn("persistence_retry_scheduler_shutdown_timeout");
        retryScheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      retryScheduler.shutdownNow();
    }

    flush();

    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        LOG.warn("persistence_writer_shutdown_timeout");
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
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

  /** Persists user profiles asynchronously. */
  public void saveUserProfiles(Map<String, UserProfile> profiles) {
    scheduleWrite(profilesFile, profiles);
  }

  /** Persists user schedules for the given year asynchronously. */
  public void saveUserSchedules(
      int year, Map<String, Map<String, UserScheduleService.TalkDetails>> schedules) {
    scheduleWrite(scheduleFile(year), schedules);
  }

  /** Loads events from disk or returns an empty map if none. */
  public Map<String, Event> loadEvents() {
    return read(eventsFile, new TypeReference<Map<String, Event>>() {
    });
  }

  /** Loads speakers from disk or returns an empty map if none. */
  public Map<String, Speaker> loadSpeakers() {
    return read(speakersFile, new TypeReference<Map<String, Speaker>>() {
    });
  }

  /** Loads user profiles from disk or returns an empty map if none. */
  public Map<String, UserProfile> loadUserProfiles() {
    return read(profilesFile, new TypeReference<Map<String, UserProfile>>() {
    });
  }

  /**
   * Loads user schedules for the given year from disk or returns an empty map if
   * none.
   */
  public Map<String, Map<String, UserScheduleService.TalkDetails>> loadUserSchedules(int year) {
    return read(
        scheduleFile(year),
        new TypeReference<Map<String, Map<String, UserScheduleService.TalkDetails>>>() {
        });
  }

  /** Lists all years that have user schedule files. */
  public Set<Integer> listUserScheduleYears() {
    try (var stream = Files.list(dataDir)) {
      return stream
          .map(Path::getFileName)
          .map(Path::toString)
          .filter(n -> n.startsWith(SCHEDULE_FILE_PREFIX) && n.endsWith(SCHEDULE_FILE_SUFFIX))
          .map(
              n -> n.substring(
                  SCHEDULE_FILE_PREFIX.length(), n.length() - SCHEDULE_FILE_SUFFIX.length()))
          .map(
              s -> {
                try {
                  return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                  return null;
                }
              })
          .filter(Objects::nonNull)
          .collect(Collectors.toCollection(TreeSet::new));
    } catch (IOException e) {
      LOG.error("Failed to list user schedule years", e);
      return Set.of();
    }
  }

  /**
   * Returns the most recent user schedule year within the last year or the
   * current year if none.
   */
  public int findLatestUserScheduleYear() {
    int currentYear = java.time.Year.now().getValue();
    try (var stream = Files.list(dataDir)) {
      var years = stream
          .map(Path::getFileName)
          .map(Path::toString)
          .filter(n -> n.startsWith(SCHEDULE_FILE_PREFIX) && n.endsWith(SCHEDULE_FILE_SUFFIX))
          .map(
              n -> n.substring(
                  SCHEDULE_FILE_PREFIX.length(),
                  n.length() - SCHEDULE_FILE_SUFFIX.length()))
          .map(
              s -> {
                try {
                  return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                  return -1;
                }
              })
          .filter(y -> y >= currentYear - 1)
          .collect(Collectors.toList());
      return years.isEmpty()
          ? currentYear
          : years.stream().mapToInt(Integer::intValue).max().orElse(currentYear);
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

  public record QueueStats(
      int depth,
      int max,
      long oldestAgeMs,
      long writesOk,
      long writesFail,
      long writesRetries,
      long droppedDueCapacity,
      String lastError) {
  }

  public QueueStats getQueueStats() {
    long oldest = 0L;
    Runnable head = queue == null ? null : queue.peek();
    if (head instanceof QueueItem qi) {
      oldest = System.currentTimeMillis() - qi.enqueued;
    }
    return new QueueStats(
        queue == null ? 0 : queue.size(),
        queueMax,
        oldest,
        writesOk.get(),
        writesFail.get(),
        writesRetries.get(),
        queueDropped.get(),
        lastError);
  }

  private void scheduleWrite(Path file, Object data) {
    scheduleWriteAttempt(file, data, 1, 250);
  }

  private void scheduleWriteAttempt(Path file, Object data, int attempt, long backoffMillis) {
    QueueItem item = new QueueItem(() -> writeOnce(file, data, attempt, backoffMillis));
    try {
      executor.execute(item);
    } catch (RejectedExecutionException e) {
      queueDropped.incrementAndGet();
      lastError = "queue_full";
      LOG.warn("Persistence queue full - dropping write for " + file.getFileName());
    }
  }

  private void writeOnce(Path file, Object data, int attempt, long backoffMillis) {
    checkDiskSpace();
    if (lowDiskSpace) {
      LOG.warn("Low disk space - skipping persistence");
      return;
    }
    try {
      Path tmp = Files.createTempFile(dataDir, file.getFileName().toString(), ".tmp");
      try {
        mapper.writeValue(tmp.toFile(), data);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        LOG.infof("Persisted %s at %s", file.getFileName(), java.time.Instant.now());
        writesOk.incrementAndGet();
      } finally {
        try {
          Files.deleteIfExists(tmp);
        } catch (IOException ignore) {
          // ignore cleanup errors
        }
      }
    } catch (IOException e) {
      if (attempt >= 3) {
        LOG.error("Failed to persist data to " + file, e);
        writesFail.incrementAndGet();
        lastError = e.getMessage();
      } else {
        LOG.warn(
            "Persistence attempt "
                + attempt
                + " failed for "
                + file
                + ", retrying in "
                + backoffMillis
                + "ms");
        writesRetries.incrementAndGet();
        scheduleRetry(file, data, attempt + 1, backoffMillis * 2);
      }
    }
  }

  private void scheduleRetry(Path file, Object data, int nextAttempt, long backoffMillis) {
    try {
      retryScheduler.schedule(
          () -> scheduleWriteAttempt(file, data, nextAttempt, backoffMillis),
          backoffMillis,
          TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      queueDropped.incrementAndGet();
      lastError = "queue_full";
      LOG.warn("Persistence queue full - dropping write for " + file.getFileName());
    }
  }

  private static class QueueItem implements Runnable {
    final Runnable task;
    final long enqueued;

    QueueItem(Runnable task) {
      this.task = task;
      this.enqueued = System.currentTimeMillis();
    }

    @Override
    public void run() {
      task.run();
    }
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
      Future<?> f = executor.submit(() -> {
      });
      f.get();
    } catch (Exception e) {
      // ignore
    }
  }

  private Path scheduleFile(int year) {
    return dataDir.resolve(SCHEDULE_FILE_PREFIX + year + SCHEDULE_FILE_SUFFIX);
  }
}
