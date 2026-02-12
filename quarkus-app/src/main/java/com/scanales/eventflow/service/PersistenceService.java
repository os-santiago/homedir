package com.scanales.eventflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.model.SystemError;
import com.scanales.eventflow.community.CommunitySubmission;
import com.scanales.eventflow.cfp.CfpConfig;
import com.scanales.eventflow.cfp.CfpSubmission;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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

  @ConfigProperty(name = "homedir.data.dir", defaultValue = "data")
  String dataDirPath = "data";

  private Path dataDir;
  private Path eventsFile;
  private Path speakersFile;
  private Path profilesFile;
  private Path systemErrorsFile;
  private Path communitySubmissionsFile;
  private Path cfpSubmissionsFile;
  private Path cfpConfigFile;
  private Path cfpBackupsDir;
  private static final String SCHEDULE_FILE_PREFIX = "user-schedule-";
  private static final String SCHEDULE_FILE_SUFFIX = ".json";
  private static final String CFP_BACKUP_PREFIX = "cfp-submissions-";
  private static final String CFP_BACKUP_SUFFIX = ".json";
  private static final DateTimeFormatter CFP_BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

  private volatile boolean lowDiskSpace;
  private static final long MIN_FREE_BYTES = 50L * 1024 * 1024; // 50 MB
  private static final int MAX_WRITE_ATTEMPTS = 3;
  private static final long INITIAL_RETRY_BACKOFF_MS = 250L;
  private static final long FLUSH_TIMEOUT_MS = 15000L;
  private final AtomicInteger scheduledRetries = new AtomicInteger();

  @ConfigProperty(name = "cfp.persistence.backups.enabled", defaultValue = "true")
  boolean cfpBackupsEnabled = true;

  @ConfigProperty(name = "cfp.persistence.backups.max-files", defaultValue = "120")
  int cfpBackupsMaxFiles = 120;

  @ConfigProperty(name = "cfp.persistence.backups.min-interval-ms", defaultValue = "300000")
  long cfpBackupsMinIntervalMs = 300_000L;

  private final AtomicLong lastCfpBackupAtMillis = new AtomicLong(0L);

  @PostConstruct
  void init() {
    String sysProp = System.getProperty("homedir.data.dir");
    if (sysProp != null && !sysProp.isBlank()) {
      dataDirPath = sysProp;
    }
    mapper = objectMapper
        .copy()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new JavaTimeModule());

    dataDir = Paths.get(dataDirPath);
    eventsFile = dataDir.resolve("events.json");
    speakersFile = dataDir.resolve("speakers.json");
    profilesFile = dataDir.resolve("user-profiles.json");
    systemErrorsFile = dataDir.resolve("system-errors.json");
    communitySubmissionsFile = dataDir.resolve("community").resolve("submissions").resolve("pending.json");
    cfpSubmissionsFile = dataDir.resolve("cfp-submissions.json");
    cfpConfigFile = dataDir.resolve("cfp-config.json");
    cfpBackupsDir = dataDir.resolve("backups").resolve("cfp");

    try {
      Files.createDirectories(dataDir);
      Files.createDirectories(cfpBackupsDir);
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
    flush();

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

  /** Persists system errors synchronously (or async) */
  public void saveSystemErrors(Map<String, SystemError> errors) {
    scheduleWrite(systemErrorsFile, errors);
  }

  /** Loads system errors */
  public Map<String, SystemError> loadSystemErrors() {
    return read(systemErrorsFile, new TypeReference<Map<String, SystemError>>() {
    });
  }

  /** Persists community submissions asynchronously. */
  public void saveCommunitySubmissions(Map<String, CommunitySubmission> submissions) {
    scheduleWrite(communitySubmissionsFile, submissions);
  }

  /** Persists community submissions synchronously. */
  public void saveCommunitySubmissionsSync(Map<String, CommunitySubmission> submissions) {
    writeSync(communitySubmissionsFile, submissions);
  }

  /** Loads community submissions from disk or returns an empty map if none. */
  public Map<String, CommunitySubmission> loadCommunitySubmissions() {
    return read(communitySubmissionsFile, new TypeReference<Map<String, CommunitySubmission>>() {
    });
  }

  /** Last modified timestamp for community submissions file, or -1 when unavailable. */
  public long communitySubmissionsLastModifiedMillis() {
    try {
      if (!Files.exists(communitySubmissionsFile)) {
        return -1L;
      }
      return Files.getLastModifiedTime(communitySubmissionsFile).toMillis();
    } catch (IOException e) {
      return -1L;
    }
  }

  /** Persists CFP submissions asynchronously. */
  public void saveCfpSubmissions(Map<String, CfpSubmission> submissions) {
    scheduleWrite(cfpSubmissionsFile, submissions);
  }

  /** Persists CFP submissions synchronously. */
  public void saveCfpSubmissionsSync(Map<String, CfpSubmission> submissions) {
    writeSync(cfpSubmissionsFile, submissions == null ? Map.of() : Map.copyOf(submissions));
  }

  /** Loads CFP submissions from disk or returns an empty map if none. */
  public Map<String, CfpSubmission> loadCfpSubmissions() {
    return readCfpSubmissionsWithRecovery();
  }

  /** Last modified timestamp for CFP submissions file, or -1 when unavailable. */
  public long cfpSubmissionsLastModifiedMillis() {
    try {
      if (!Files.exists(cfpSubmissionsFile)) {
        return -1L;
      }
      return Files.getLastModifiedTime(cfpSubmissionsFile).toMillis();
    } catch (IOException e) {
      return -1L;
    }
  }

  /** Loads CFP config from disk if present. */
  public java.util.Optional<CfpConfig> loadCfpConfig() {
    if (cfpConfigFile == null || !Files.exists(cfpConfigFile)) {
      return java.util.Optional.empty();
    }
    try {
      return java.util.Optional.ofNullable(mapper.readValue(cfpConfigFile.toFile(), CfpConfig.class));
    } catch (IOException e) {
      LOG.error("Failed to read " + cfpConfigFile.toAbsolutePath(), e);
      return java.util.Optional.empty();
    }
  }

  /** Persists CFP config synchronously. */
  public void saveCfpConfigSync(CfpConfig config) {
    writeSync(cfpConfigFile, config);
  }

  /** Last modified timestamp for CFP config file, or -1 when unavailable. */
  public long cfpConfigLastModifiedMillis() {
    try {
      if (cfpConfigFile == null || !Files.exists(cfpConfigFile)) {
        return -1L;
      }
      return Files.getLastModifiedTime(cfpConfigFile).toMillis();
    } catch (IOException e) {
      return -1L;
    }
  }

  /** Returns operational storage metadata for CFP persistence and backups. */
  public CfpStorageInfo cfpStorageInfo() {
    boolean primaryExists = Files.exists(cfpSubmissionsFile);
    long primarySizeBytes = fileSize(cfpSubmissionsFile);
    long primaryLastModifiedMillis = cfpSubmissionsLastModifiedMillis();

    int backupCount = 0;
    String latestBackupName = null;
    long latestBackupSizeBytes = -1L;
    long latestBackupLastModifiedMillis = -1L;

    if (Files.exists(cfpBackupsDir)) {
      try (var stream = Files.list(cfpBackupsDir)) {
        var backups =
            stream
                .filter(Files::isRegularFile)
                .filter(this::isCfpBackupFile)
                .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                .toList();
        backupCount = backups.size();
        if (!backups.isEmpty()) {
          Path latest = backups.getFirst();
          latestBackupName = latest.getFileName() == null ? null : latest.getFileName().toString();
          latestBackupSizeBytes = fileSize(latest);
          latestBackupLastModifiedMillis = fileLastModifiedMillis(latest);
        }
      } catch (IOException e) {
        LOG.warn("Failed to inspect CFP backup directory", e);
      }
    }

    return new CfpStorageInfo(
        cfpSubmissionsFile.toAbsolutePath().toString(),
        cfpBackupsDir.toAbsolutePath().toString(),
        primaryExists,
        primarySizeBytes,
        primaryLastModifiedMillis,
        backupCount,
        latestBackupName,
        latestBackupSizeBytes,
        latestBackupLastModifiedMillis);
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

  public record CfpStorageInfo(
      String primaryPath,
      String backupsPath,
      boolean primaryExists,
      long primarySizeBytes,
      long primaryLastModifiedMillis,
      int backupCount,
      String latestBackupName,
      long latestBackupSizeBytes,
      long latestBackupLastModifiedMillis) {
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

  // Version tracking for files to prevent stale overwrites and coalesce writes
  private final ConcurrentHashMap<Path, AtomicLong> fileVersions = new ConcurrentHashMap<>();

  private void scheduleWrite(Path file, Object data) {
    AtomicLong versionCounter = fileVersions.computeIfAbsent(file, k -> new AtomicLong(0));
    long version = versionCounter.incrementAndGet();
    scheduleWriteAttempt(file, data, version, 1, INITIAL_RETRY_BACKOFF_MS);
  }

  private void scheduleWriteAttempt(
      Path file, Object data, long version, int attempt, long backoffMillis) {
    QueueItem item = new QueueItem(() -> writeOnce(file, data, version, attempt, backoffMillis));
    try {
      executor.execute(item);
    } catch (RejectedExecutionException e) {
      queueDropped.incrementAndGet();
      lastError = "queue_full";
      LOG.warn("Persistence queue full - dropping write for " + file.getFileName());
    }
  }

  private void writeOnce(Path file, Object data, long version, int attempt, long backoffMillis) {
    // Stale check: if a newer version has been scheduled, skip this write.
    // This handles both optimization (skipping intermediate states) and correctness
    // (preventing stale retries).
    AtomicLong currentCounter = fileVersions.get(file);
    if (currentCounter != null && currentCounter.get() > version) {
      LOG.debugf("Skipping stale write for %s (v%d < v%d)", file.getFileName(), version, currentCounter.get());
      return;
    }

    checkDiskSpace();
    if (lowDiskSpace) {
      if (attempt >= MAX_WRITE_ATTEMPTS) {
        writesFail.incrementAndGet();
        lastError = "low_disk_space";
        LOG.errorf(
            "Low disk space after %d attempts - dropping write for %s (v%d)",
            attempt,
            file.getFileName(),
            version);
      } else {
        writesRetries.incrementAndGet();
        lastError = "low_disk_space_retry";
        LOG.warnf(
            "Low disk space on attempt %d for %s (v%d), retrying in %dms",
            attempt,
            file.getFileName(),
            version,
            backoffMillis);
        scheduleRetry(file, data, version, attempt + 1, backoffMillis * 2);
      }
      return;
    }
    try {
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Path tmp = Files.createTempFile(dataDir, file.getFileName().toString(), ".tmp");
      try {
        mapper.writeValue(tmp.toFile(), data);
        moveWithFallback(tmp, file);
        maybeBackupCfpSubmissions(file);
        LOG.infof("Persisted %s at %s (v%d)", file.getFileName(), java.time.Instant.now(), version);
        writesOk.incrementAndGet();
        lastError = null;
      } finally {
        try {
          Files.deleteIfExists(tmp);
        } catch (IOException ignore) {
          // ignore cleanup errors
        }
      }
    } catch (IOException e) {
      if (attempt >= MAX_WRITE_ATTEMPTS) {
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
        // Important: Pass the ORIGINAL version to the retry so it remains verifiable
        // against current state
        scheduleRetry(file, data, version, attempt + 1, backoffMillis * 2);
      }
    }
  }

  private void writeSync(Path file, Object data) {
    checkDiskSpace();
    if (lowDiskSpace) {
      writesFail.incrementAndGet();
      lastError = "low_disk_space";
      throw new IllegalStateException("low_disk_space");
    }
    try {
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Path tmp = Files.createTempFile(dataDir, file.getFileName().toString(), ".tmp");
      try {
        mapper.writeValue(tmp.toFile(), data);
        moveWithFallback(tmp, file);
        maybeBackupCfpSubmissions(file);
        LOG.infof("Persisted %s at %s (sync)", file.getFileName(), java.time.Instant.now());
        writesOk.incrementAndGet();
        lastError = null;
      } finally {
        try {
          Files.deleteIfExists(tmp);
        } catch (IOException ignore) {
          // ignore cleanup errors
        }
      }
    } catch (IOException e) {
      writesFail.incrementAndGet();
      lastError = e.getMessage();
      throw new IllegalStateException("failed_to_persist_data", e);
    }
  }

  private void scheduleRetry(
      Path file, Object data, long version, int nextAttempt, long backoffMillis) {
    scheduledRetries.incrementAndGet();
    try {
      retryScheduler.schedule(
          () -> {
            try {
              scheduleWriteAttempt(file, data, version, nextAttempt, backoffMillis);
            } finally {
              scheduledRetries.decrementAndGet();
            }
          },
          backoffMillis,
          TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      scheduledRetries.decrementAndGet();
      queueDropped.incrementAndGet();
      lastError = "retry_scheduler_rejected";
      LOG.warn("Persistence retry scheduler rejected write for " + file.getFileName());
    }
  }

  private static void moveWithFallback(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
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

  private Map<String, CfpSubmission> readCfpSubmissionsWithRecovery() {
    TypeReference<Map<String, CfpSubmission>> type = new TypeReference<Map<String, CfpSubmission>>() {
    };
    if (!Files.exists(cfpSubmissionsFile)) {
      Map<String, CfpSubmission> recovered = recoverCfpFromBackups(type, "primary_missing");
      if (recovered != null) {
        return recovered;
      }
      LOG.infof("No persistence file %s found - starting empty", cfpSubmissionsFile.toAbsolutePath());
      return new ConcurrentHashMap<>();
    }
    try {
      Map<String, CfpSubmission> data = mapper.readValue(cfpSubmissionsFile.toFile(), type);
      LOG.infof("Loaded %d entries from %s", data.size(), cfpSubmissionsFile.toAbsolutePath());
      return new ConcurrentHashMap<>(data);
    } catch (IOException e) {
      LOG.error("Failed to read " + cfpSubmissionsFile.toAbsolutePath(), e);
      quarantineCorruptedCfpPrimary();
      Map<String, CfpSubmission> recovered = recoverCfpFromBackups(type, "primary_corrupted");
      if (recovered != null) {
        return recovered;
      }
      return new ConcurrentHashMap<>();
    }
  }

  private Map<String, CfpSubmission> recoverCfpFromBackups(
      TypeReference<Map<String, CfpSubmission>> type, String reason) {
    if (!Files.exists(cfpBackupsDir)) {
      return null;
    }
    try (var stream = Files.list(cfpBackupsDir)) {
      var candidates =
          stream
              .filter(Files::isRegularFile)
              .filter(this::isCfpBackupFile)
              .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
              .toList();
      for (Path candidate : candidates) {
        try {
          Map<String, CfpSubmission> data = mapper.readValue(candidate.toFile(), type);
          LOG.warnf(
              "Recovered CFP submissions from backup %s (%d items, reason=%s)",
              candidate.toAbsolutePath(),
              data.size(),
              reason);
          writeSync(cfpSubmissionsFile, data);
          return new ConcurrentHashMap<>(data);
        } catch (Exception ignored) {
          // keep trying with older snapshots
        }
      }
      LOG.infof("No valid CFP backup snapshot found in %s", cfpBackupsDir.toAbsolutePath());
    } catch (IOException e) {
      LOG.error("Failed to inspect CFP backup snapshots", e);
    }
    return null;
  }

  private void maybeBackupCfpSubmissions(Path file) {
    if (!cfpBackupsEnabled || !file.equals(cfpSubmissionsFile) || !Files.exists(cfpSubmissionsFile)) {
      return;
    }
    long now = System.currentTimeMillis();
    long previous = lastCfpBackupAtMillis.get();
    if (previous > 0 && (now - previous) < Math.max(0L, cfpBackupsMinIntervalMs)) {
      return;
    }
    if (!lastCfpBackupAtMillis.compareAndSet(previous, now)) {
      return;
    }
    try {
      Files.createDirectories(cfpBackupsDir);
      String stamp = LocalDateTime.now().format(CFP_BACKUP_TIME);
      Path snapshot = cfpBackupsDir.resolve(CFP_BACKUP_PREFIX + stamp + CFP_BACKUP_SUFFIX);
      Files.copy(cfpSubmissionsFile, snapshot, StandardCopyOption.REPLACE_EXISTING);
      pruneOldCfpBackups();
      LOG.infof("Persisted CFP backup snapshot %s", snapshot.getFileName());
    } catch (IOException e) {
      LOG.warn("Failed to persist CFP backup snapshot", e);
      lastCfpBackupAtMillis.compareAndSet(now, previous);
    }
  }

  private void pruneOldCfpBackups() {
    int keep = Math.max(1, cfpBackupsMaxFiles);
    try (var stream = Files.list(cfpBackupsDir)) {
      var files =
          stream
              .filter(Files::isRegularFile)
              .filter(this::isCfpBackupFile)
              .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
              .toList();
      for (int i = keep; i < files.size(); i++) {
        Files.deleteIfExists(files.get(i));
      }
    } catch (IOException e) {
      LOG.warn("Failed to prune CFP backup snapshots", e);
    }
  }

  private void quarantineCorruptedCfpPrimary() {
    if (!Files.exists(cfpSubmissionsFile)) {
      return;
    }
    String stamp = LocalDateTime.now().format(CFP_BACKUP_TIME);
    Path quarantined = cfpSubmissionsFile.resolveSibling("cfp-submissions.corrupt-" + stamp + ".json");
    try {
      Files.move(cfpSubmissionsFile, quarantined, StandardCopyOption.REPLACE_EXISTING);
      LOG.warnf("Moved corrupted CFP submissions file to %s", quarantined.toAbsolutePath());
    } catch (IOException e) {
      LOG.warn("Failed to quarantine corrupted CFP submissions file", e);
    }
  }

  private boolean isCfpBackupFile(Path file) {
    String name = file.getFileName() == null ? "" : file.getFileName().toString();
    return name.startsWith(CFP_BACKUP_PREFIX) && name.endsWith(CFP_BACKUP_SUFFIX);
  }

  private long fileSize(Path file) {
    try {
      if (!Files.exists(file)) {
        return -1L;
      }
      return Files.size(file);
    } catch (IOException e) {
      return -1L;
    }
  }

  private long fileLastModifiedMillis(Path file) {
    try {
      if (!Files.exists(file)) {
        return -1L;
      }
      return Files.getLastModifiedTime(file).toMillis();
    } catch (IOException e) {
      return -1L;
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
    if (executor == null || retryScheduler == null) {
      return;
    }
    long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FLUSH_TIMEOUT_MS);
    while (System.nanoTime() < deadlineNanos) {
      if (executor.isShutdown()) {
        return;
      }
      try {
        Future<?> f = executor.submit(() -> {
        });
        f.get(2, TimeUnit.SECONDS);
      } catch (RejectedExecutionException ignored) {
        return;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (ExecutionException | TimeoutException ignored) {
        // keep trying until deadline
      }

      if (queue.isEmpty() && executor.getActiveCount() == 0 && scheduledRetries.get() == 0) {
        return;
      }

      try {
        Thread.sleep(25);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    LOG.warnf(
        "persistence_flush_timeout depth=%d active=%d pendingRetries=%d",
        queue.size(),
        executor.getActiveCount(),
        scheduledRetries.get());
  }

  private Path scheduleFile(int year) {
    return dataDir.resolve(SCHEDULE_FILE_PREFIX + year + SCHEDULE_FILE_SUFFIX);
  }
}

