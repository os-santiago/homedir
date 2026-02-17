package com.scanales.eventflow.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
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

  @ConfigProperty(name = "persist.coalesce.window-ms", defaultValue = "350")
  long writeCoalesceWindowMs = 350L;

  @ConfigProperty(name = "persist.coalesce.max-pending-files", defaultValue = "4096")
  int writeCoalesceMaxPendingFiles = 4096;

  // Durability / validation knobs. Defaults are conservative for sync writes,
  // while async fsync is opt-in to avoid surprising latency until we add write coalescing.
  @ConfigProperty(name = "persist.verify-json.enabled", defaultValue = "true")
  boolean verifyJsonEnabled = true;

  @ConfigProperty(name = "persist.fsync.sync.enabled", defaultValue = "true")
  boolean fsyncSyncEnabled = true;

  @ConfigProperty(name = "persist.fsync.async.enabled", defaultValue = "false")
  boolean fsyncAsyncEnabled = false;

  private final AtomicLong writesOk = new AtomicLong();
  private final AtomicLong writesFail = new AtomicLong();
  private final AtomicLong writesRetries = new AtomicLong();
  private final AtomicLong writesCoalesced = new AtomicLong();
  private final AtomicLong writesCoalesceBypassed = new AtomicLong();
  private final AtomicLong queueDropped = new AtomicLong();
  private volatile String lastError;
  private volatile String lastWriteFile;
  private final AtomicLong lastWriteAtMillis = new AtomicLong();
  private final AtomicLong lastWriteDurationMs = new AtomicLong();
  private final AtomicLong lastWriteBytes = new AtomicLong();

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
  private Path cfpWalFile;
  private static final String SCHEDULE_FILE_PREFIX = "user-schedule-";
  private static final String SCHEDULE_FILE_SUFFIX = ".json";
  private static final String CFP_BACKUP_PREFIX = "cfp-submissions-";
  private static final String CFP_BACKUP_SUFFIX = ".json";
  private static final String CFP_WAL_FILE_NAME = "cfp-submissions.wal";
  private static final int CFP_WAL_RECORD_HEADER_BYTES = Integer.BYTES;
  private static final int CFP_SUBMISSIONS_SCHEMA_VERSION = 1;
  private static final String CFP_SUBMISSIONS_KIND = "cfp_submissions";
  private static final String CFP_SUBMISSIONS_FIELD = "submissions";
  private static final String CFP_SCHEMA_FIELD = "schema_version";
  private static final String CFP_SCHEMA_FIELD_ALT = "schemaVersion";
  private static final String CFP_CHECKSUM_FIELD = "checksum_sha256";
  private static final TypeReference<Map<String, CfpSubmission>> CFP_SUBMISSIONS_TYPE =
      new TypeReference<Map<String, CfpSubmission>>() {
      };
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

  @ConfigProperty(name = "cfp.persistence.wal.enabled", defaultValue = "true")
  boolean cfpWalEnabled = true;

  @ConfigProperty(name = "cfp.persistence.wal.fsync.enabled", defaultValue = "true")
  boolean cfpWalFsyncEnabled = true;

  @ConfigProperty(name = "cfp.persistence.wal.max-bytes", defaultValue = "10485760")
  long cfpWalMaxBytes = 10L * 1024L * 1024L;

  @ConfigProperty(name = "cfp.persistence.checksum.enabled", defaultValue = "true")
  boolean cfpChecksumEnabled = true;

  @ConfigProperty(name = "cfp.persistence.checksum.required", defaultValue = "false")
  boolean cfpChecksumRequired = false;

  private final AtomicLong lastCfpBackupAtMillis = new AtomicLong(0L);
  private final AtomicLong cfpWalAppends = new AtomicLong();
  private final AtomicLong cfpWalCompactions = new AtomicLong();
  private final AtomicLong cfpWalRecoveries = new AtomicLong();
  private final AtomicLong cfpChecksumMismatches = new AtomicLong();
  private final AtomicLong cfpChecksumHydrations = new AtomicLong();

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
    cfpWalFile = dataDir.resolve(CFP_WAL_FILE_NAME);

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
    long walSizeBytes = fileSize(cfpWalFile);
    long walLastModifiedMillis = fileLastModifiedMillis(cfpWalFile);

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
        latestBackupLastModifiedMillis,
        cfpWalEnabled,
        cfpWalFile.toAbsolutePath().toString(),
        walSizeBytes,
        walLastModifiedMillis,
        cfpWalAppends.get(),
        cfpWalCompactions.get(),
        cfpWalRecoveries.get(),
        cfpChecksumEnabled,
        cfpChecksumRequired,
        cfpChecksumMismatches.get(),
        cfpChecksumHydrations.get());
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
      long latestBackupLastModifiedMillis,
      boolean walEnabled,
      String walPath,
      long walSizeBytes,
      long walLastModifiedMillis,
      long walAppends,
      long walCompactions,
      long walRecoveries,
      boolean checksumEnabled,
      boolean checksumRequired,
      long checksumMismatches,
      long checksumHydrations) {
  }

  private record CfpSubmissionsEnvelope(
      @JsonProperty(CFP_SCHEMA_FIELD) int schemaVersion,
      @JsonProperty("kind") String kind,
      @JsonProperty("updated_at") String updatedAt,
      @JsonProperty(CFP_CHECKSUM_FIELD) String checksumSha256,
      @JsonProperty(CFP_SUBMISSIONS_FIELD) Map<String, CfpSubmission> submissions) {
  }

  private record CfpSnapshotRead(
      Map<String, CfpSubmission> submissions,
      int schemaVersion,
      boolean legacy,
      boolean missingChecksum) {
  }

  public record QueueStats(
      int depth,
      int max,
      long oldestAgeMs,
      long writesOk,
      long writesFail,
      long writesRetries,
      long writesCoalesced,
      long writesCoalesceBypassed,
      long droppedDueCapacity,
      String lastError,
      String lastWriteFile,
      long lastWriteAtMillis,
      long lastWriteDurationMs,
      long lastWriteBytes) {
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
        writesCoalesced.get(),
        writesCoalesceBypassed.get(),
        queueDropped.get(),
        lastError,
        lastWriteFile,
        lastWriteAtMillis.get(),
        lastWriteDurationMs.get(),
        lastWriteBytes.get());
  }

  // Version tracking for files to prevent stale overwrites and coalesce writes
  private final ConcurrentHashMap<Path, AtomicLong> fileVersions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Path, PendingWrite> pendingWrites = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Path, ScheduledFuture<?>> debounceFutures = new ConcurrentHashMap<>();

  private void scheduleWrite(Path file, Object data) {
    AtomicLong versionCounter = fileVersions.computeIfAbsent(file, k -> new AtomicLong(0));
    long version = versionCounter.incrementAndGet();
    PendingWrite pending = new PendingWrite(data, version);
    PendingWrite previous = pendingWrites.put(file, pending);
    if (previous != null) {
      writesCoalesced.incrementAndGet();
    }

    if (writeCoalesceWindowMs <= 0L) {
      pendingWrites.remove(file, pending);
      cancelDebounceFuture(file);
      scheduleWriteAttempt(file, data, version, 1, INITIAL_RETRY_BACKOFF_MS);
      return;
    }

    if (writeCoalesceMaxPendingFiles > 0
        && pendingWrites.size() > writeCoalesceMaxPendingFiles
        && previous == null) {
      // Backpressure: do not let pending debounced state grow unbounded by file key.
      writesCoalesceBypassed.incrementAndGet();
      pendingWrites.remove(file, pending);
      cancelDebounceFuture(file);
      scheduleWriteAttempt(file, data, version, 1, INITIAL_RETRY_BACKOFF_MS);
      return;
    }

    ScheduledFuture<?> future =
        retryScheduler.schedule(() -> dispatchDebouncedWrite(file, version), writeCoalesceWindowMs, TimeUnit.MILLISECONDS);
    ScheduledFuture<?> previousFuture = debounceFutures.put(file, future);
    if (previousFuture != null && !previousFuture.isDone()) {
      previousFuture.cancel(false);
    }
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
      Path tmpDir = parent != null ? parent : dataDir;
      Path tmp = Files.createTempFile(tmpDir, file.getFileName().toString(), ".tmp");
      Object persistedData = preparePersistedData(file, data);
      long start = System.nanoTime();
      try {
        long bytes = writeJsonToTemp(tmp, persistedData, fsyncAsyncEnabled);
        appendCfpWalIfNeeded(file, persistedData);
        moveWithFallback(tmp, file);
        maybeBackupCfpSubmissions(file);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        recordWriteSuccess(file, bytes, durationMs);
        LOG.infof(
            "Persisted %s at %s (v%d, bytes=%d, durationMs=%d)",
            file.getFileName(),
            java.time.Instant.now(),
            version,
            bytes,
            durationMs);
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
      Path tmpDir = parent != null ? parent : dataDir;
      Path tmp = Files.createTempFile(tmpDir, file.getFileName().toString(), ".tmp");
      Object persistedData = preparePersistedData(file, data);
      long start = System.nanoTime();
      try {
        long bytes = writeJsonToTemp(tmp, persistedData, fsyncSyncEnabled);
        appendCfpWalIfNeeded(file, persistedData);
        moveWithFallback(tmp, file);
        maybeBackupCfpSubmissions(file);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        recordWriteSuccess(file, bytes, durationMs);
        LOG.infof(
            "Persisted %s at %s (sync, bytes=%d, durationMs=%d)",
            file.getFileName(),
            java.time.Instant.now(),
            bytes,
            durationMs);
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

  private void dispatchDebouncedWrite(Path file, long version) {
    PendingWrite pending = pendingWrites.get(file);
    if (pending == null || pending.version != version) {
      return;
    }
    if (!pendingWrites.remove(file, pending)) {
      return;
    }
    ScheduledFuture<?> current = debounceFutures.get(file);
    if (current != null && (current.isDone() || current.isCancelled())) {
      debounceFutures.remove(file, current);
    }
    scheduleWriteAttempt(file, pending.data, pending.version, 1, INITIAL_RETRY_BACKOFF_MS);
  }

  private void cancelDebounceFuture(Path file) {
    ScheduledFuture<?> previousFuture = debounceFutures.remove(file);
    if (previousFuture != null && !previousFuture.isDone()) {
      previousFuture.cancel(false);
    }
  }

  private void flushDebouncedWritesNow() {
    if (pendingWrites.isEmpty()) {
      return;
    }
    for (Map.Entry<Path, PendingWrite> entry : pendingWrites.entrySet()) {
      Path file = entry.getKey();
      PendingWrite pending = entry.getValue();
      if (pending == null) {
        continue;
      }
      if (!pendingWrites.remove(file, pending)) {
        continue;
      }
      cancelDebounceFuture(file);
      scheduleWriteAttempt(file, pending.data, pending.version, 1, INITIAL_RETRY_BACKOFF_MS);
    }
  }

  private static void moveWithFallback(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private long writeJsonToTemp(Path tmp, Object data, boolean fsync) throws IOException {
    byte[] payload = mapper.writeValueAsBytes(data);
    if (verifyJsonEnabled) {
      // Ensures we never persist invalid JSON even if the filesystem write succeeds.
      mapper.readTree(payload);
    }
    try (FileChannel channel =
        FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      channel.write(ByteBuffer.wrap(payload));
      if (fsync) {
        channel.force(true);
      }
    }
    return payload.length;
  }

  private Object preparePersistedData(Path file, Object data) {
    if (!file.equals(cfpSubmissionsFile)) {
      return data;
    }
    Map<String, CfpSubmission> snapshot = normalizeCfpSnapshot(data);
    if (snapshot == null) {
      return data;
    }
    return cfpEnvelope(snapshot);
  }

  private CfpSubmissionsEnvelope cfpEnvelope(Map<String, CfpSubmission> snapshot) {
    String checksum = cfpChecksumEnabled ? computeCfpChecksum(snapshot) : null;
    return new CfpSubmissionsEnvelope(
        CFP_SUBMISSIONS_SCHEMA_VERSION,
        CFP_SUBMISSIONS_KIND,
        Instant.now().toString(),
        checksum,
        new java.util.LinkedHashMap<>(snapshot));
  }

  private String computeCfpChecksum(Map<String, CfpSubmission> submissions) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] payload = mapper.writeValueAsBytes(new java.util.TreeMap<>(submissions));
      return HexFormat.of().formatHex(digest.digest(payload));
    } catch (NoSuchAlgorithmException | IOException e) {
      throw new IllegalStateException("failed_cfp_checksum", e);
    }
  }

  private void appendCfpWalIfNeeded(Path file, Object data) throws IOException {
    if (!cfpWalEnabled || !file.equals(cfpSubmissionsFile)) {
      return;
    }
    Map<String, CfpSubmission> snapshot = normalizeCfpSnapshot(data);
    if (snapshot == null) {
      return;
    }
    appendCfpWalSnapshot(snapshot);
  }

  private Map<String, CfpSubmission> normalizeCfpSnapshot(Object data) {
    if (data instanceof CfpSubmissionsEnvelope envelope) {
      return normalizeCfpSnapshot(envelope.submissions());
    }
    if (!(data instanceof Map<?, ?> raw)) {
      return null;
    }
    java.util.LinkedHashMap<String, CfpSubmission> normalized = new java.util.LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
      if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof CfpSubmission submission)) {
        return null;
      }
      normalized.put(key, submission);
    }
    return normalized;
  }

  private void appendCfpWalSnapshot(Map<String, CfpSubmission> snapshot) throws IOException {
    Path parent = cfpWalFile.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    byte[] payload = mapper.writeValueAsBytes(cfpEnvelope(snapshot));
    if (verifyJsonEnabled) {
      mapper.readTree(payload);
    }
    try (FileChannel channel =
        FileChannel.open(cfpWalFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
      writeFully(channel, intToBytes(payload.length));
      writeFully(channel, ByteBuffer.wrap(payload));
      if (cfpWalFsyncEnabled) {
        channel.force(true);
      }
    }
    cfpWalAppends.incrementAndGet();
    maybeCompactCfpWal(snapshot);
  }

  private void maybeCompactCfpWal(Map<String, CfpSubmission> latestSnapshot) throws IOException {
    long maxBytes = Math.max(1024L, cfpWalMaxBytes);
    long size = fileSize(cfpWalFile);
    if (size <= 0L || size <= maxBytes) {
      return;
    }
    compactCfpWalToSnapshot(latestSnapshot);
  }

  private void compactCfpWalToSnapshot(Map<String, CfpSubmission> snapshot) throws IOException {
    Path parent = cfpWalFile.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Path tmp = Files.createTempFile(parent != null ? parent : dataDir, CFP_WAL_FILE_NAME, ".tmp");
    byte[] payload = mapper.writeValueAsBytes(cfpEnvelope(snapshot));
    try {
      try (FileChannel channel =
          FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
        writeFully(channel, intToBytes(payload.length));
        writeFully(channel, ByteBuffer.wrap(payload));
        if (cfpWalFsyncEnabled) {
          channel.force(true);
        }
      }
      moveWithFallback(tmp, cfpWalFile);
      cfpWalCompactions.incrementAndGet();
      LOG.infof("Compacted CFP WAL to latest snapshot (%d bytes)", payload.length);
    } finally {
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException ignore) {
        // ignore cleanup errors
      }
    }
  }

  private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining()) {
      channel.write(buffer);
    }
  }

  private static ByteBuffer intToBytes(int value) {
    ByteBuffer header = ByteBuffer.allocate(CFP_WAL_RECORD_HEADER_BYTES);
    header.putInt(value);
    header.flip();
    return header;
  }

  private void recordWriteSuccess(Path file, long bytes, long durationMs) {
    lastWriteFile = file.getFileName() == null ? file.toString() : file.getFileName().toString();
    lastWriteAtMillis.set(System.currentTimeMillis());
    lastWriteDurationMs.set(Math.max(0L, durationMs));
    lastWriteBytes.set(bytes);
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

  private static final class PendingWrite {
    final Object data;
    final long version;

    private PendingWrite(Object data, long version) {
      this.data = data;
      this.version = version;
    }
  }

  private Map<String, CfpSubmission> readCfpSubmissionsWithRecovery() {
    if (!Files.exists(cfpSubmissionsFile)) {
      Map<String, CfpSubmission> walRecovered = recoverCfpFromWal("primary_missing");
      if (walRecovered != null) {
        return walRecovered;
      }
      Map<String, CfpSubmission> recovered = recoverCfpFromBackups("primary_missing");
      if (recovered != null) {
        return recovered;
      }
      LOG.infof("No persistence file %s found - starting empty", cfpSubmissionsFile.toAbsolutePath());
      return new ConcurrentHashMap<>();
    }
    try {
      CfpSnapshotRead snapshot = parseCfpSnapshot(cfpSubmissionsFile);
      maybeMigrateCfpSnapshot(snapshot, "primary");
      LOG.infof(
          "Loaded %d entries from %s (schema=%d%s%s)",
          snapshot.submissions().size(),
          cfpSubmissionsFile.toAbsolutePath(),
          snapshot.schemaVersion(),
          snapshot.legacy() ? ", legacy" : "",
          snapshot.missingChecksum() ? ", checksum_missing" : "");
      return new ConcurrentHashMap<>(snapshot.submissions());
    } catch (IOException e) {
      LOG.error("Failed to read " + cfpSubmissionsFile.toAbsolutePath(), e);
      quarantineCorruptedCfpPrimary();
      Map<String, CfpSubmission> walRecovered = recoverCfpFromWal("primary_corrupted");
      if (walRecovered != null) {
        return walRecovered;
      }
      Map<String, CfpSubmission> recovered = recoverCfpFromBackups("primary_corrupted");
      if (recovered != null) {
        return recovered;
      }
      return new ConcurrentHashMap<>();
    }
  }

  private CfpSnapshotRead parseCfpSnapshot(Path path) throws IOException {
    return parseCfpSnapshot(mapper.readTree(path.toFile()), path.toAbsolutePath().toString());
  }

  private CfpSnapshotRead parseCfpSnapshot(byte[] payload, String source) throws IOException {
    return parseCfpSnapshot(mapper.readTree(payload), source);
  }

  private CfpSnapshotRead parseCfpSnapshot(JsonNode root, String source) throws IOException {
    if (root == null || root.isNull() || !root.isObject()) {
      throw new IOException("invalid_cfp_snapshot_shape: " + source);
    }
    JsonNode submissionsNode = root.get(CFP_SUBMISSIONS_FIELD);
    if (submissionsNode != null) {
      if (!submissionsNode.isObject()) {
        throw new IOException("invalid_cfp_submissions_node: " + source);
      }
      Map<String, CfpSubmission> submissions;
      try {
        submissions = mapper.convertValue(submissionsNode, CFP_SUBMISSIONS_TYPE);
      } catch (IllegalArgumentException e) {
        throw new IOException("invalid_cfp_submissions_payload: " + source, e);
      }
      int schemaVersion = parseSchemaVersion(root);
      String checksum = readChecksum(root);
      boolean missingChecksum = checksum == null || checksum.isBlank();
      if (missingChecksum && cfpChecksumRequired) {
        throw new IOException("missing_cfp_checksum: " + source);
      }
      if (!missingChecksum && cfpChecksumEnabled) {
        String expected = computeCfpChecksum(submissions == null ? Map.of() : submissions);
        if (!expected.equalsIgnoreCase(checksum)) {
          cfpChecksumMismatches.incrementAndGet();
          throw new IOException("invalid_cfp_checksum: " + source);
        }
      }
      return new CfpSnapshotRead(
          submissions == null ? Map.of() : new java.util.LinkedHashMap<>(submissions),
          schemaVersion,
          false,
          missingChecksum);
    }

    Map<String, CfpSubmission> legacy;
    try {
      legacy = mapper.convertValue(root, CFP_SUBMISSIONS_TYPE);
    } catch (IllegalArgumentException e) {
      throw new IOException("invalid_legacy_cfp_snapshot: " + source, e);
    }
    return new CfpSnapshotRead(
        legacy == null ? Map.of() : new java.util.LinkedHashMap<>(legacy),
        0,
        true,
        false);
  }

  private int parseSchemaVersion(JsonNode root) {
    JsonNode field = root.get(CFP_SCHEMA_FIELD);
    int parsed = parseSchemaVersionValue(field);
    if (parsed > 0) {
      return parsed;
    }
    JsonNode alt = root.get(CFP_SCHEMA_FIELD_ALT);
    parsed = parseSchemaVersionValue(alt);
    return parsed > 0 ? parsed : CFP_SUBMISSIONS_SCHEMA_VERSION;
  }

  private int parseSchemaVersionValue(JsonNode node) {
    if (node == null || node.isNull()) {
      return -1;
    }
    if (node.isInt() || node.isLong()) {
      return node.asInt(-1);
    }
    if (node.isTextual()) {
      try {
        return Integer.parseInt(node.asText());
      } catch (NumberFormatException ignored) {
        return -1;
      }
    }
    return -1;
  }

  private String readChecksum(JsonNode root) {
    JsonNode checksumNode = root.get(CFP_CHECKSUM_FIELD);
    if (checksumNode == null || checksumNode.isNull()) {
      return null;
    }
    if (!checksumNode.isTextual()) {
      return null;
    }
    return checksumNode.asText();
  }

  private void maybeMigrateCfpSnapshot(CfpSnapshotRead snapshot, String source) {
    if (!snapshot.legacy() && !snapshot.missingChecksum()) {
      return;
    }
    try {
      writeSync(cfpSubmissionsFile, snapshot.submissions());
      if (snapshot.missingChecksum()) {
        cfpChecksumHydrations.incrementAndGet();
      }
      LOG.infof(
          "Migrated CFP snapshot to schema v%d (%s%s%s)",
          CFP_SUBMISSIONS_SCHEMA_VERSION,
          source,
          snapshot.legacy() ? ", legacy" : "",
          snapshot.missingChecksum() ? ", checksum_hydrated" : "");
    } catch (RuntimeException e) {
      LOG.warnf(
          e,
          "Failed to migrate CFP snapshot from %s; continuing with in-memory data",
          source);
    }
  }

  private Map<String, CfpSubmission> recoverCfpFromBackups(String reason) {
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
          CfpSnapshotRead snapshot = parseCfpSnapshot(candidate);
          Map<String, CfpSubmission> data = snapshot.submissions();
          LOG.warnf(
              "Recovered CFP submissions from backup %s (%d items, reason=%s, schema=%d%s)",
              candidate.toAbsolutePath(),
              data.size(),
              reason,
              snapshot.schemaVersion(),
              snapshot.legacy() ? ", legacy" : "");
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

  private Map<String, CfpSubmission> recoverCfpFromWal(String reason) {
    if (!cfpWalEnabled || cfpWalFile == null || !Files.exists(cfpWalFile)) {
      return null;
    }
    try {
      byte[] walBytes = Files.readAllBytes(cfpWalFile);
      if (walBytes.length < CFP_WAL_RECORD_HEADER_BYTES) {
        return null;
      }
      ByteBuffer buffer = ByteBuffer.wrap(walBytes);
      Map<String, CfpSubmission> latest = null;
      int records = 0;
      int valid = 0;
      while (buffer.remaining() >= CFP_WAL_RECORD_HEADER_BYTES) {
        int length = buffer.getInt();
        records += 1;
        if (length <= 0 || length > buffer.remaining()) {
          // likely tail truncation due abrupt stop; keep latest valid frame
          break;
        }
        byte[] payload = new byte[length];
        buffer.get(payload);
        try {
          CfpSnapshotRead parsedSnapshot =
              parseCfpSnapshot(payload, cfpWalFile.toAbsolutePath() + "#frame-" + records);
          Map<String, CfpSubmission> parsed = parsedSnapshot.submissions();
          if (parsed != null) {
            latest = parsed;
            valid += 1;
          }
        } catch (Exception ignored) {
          // ignore a bad frame and keep scanning for a later valid one
        }
      }
      if (latest == null) {
        return null;
      }
      LOG.warnf(
          "Recovered CFP submissions from WAL %s (%d items, reason=%s, records=%d, valid=%d)",
          cfpWalFile.toAbsolutePath(),
          latest.size(),
          reason,
          records,
          valid);
      cfpWalRecoveries.incrementAndGet();
      compactCfpWalToSnapshot(latest);
      writeSync(cfpSubmissionsFile, latest);
      return new ConcurrentHashMap<>(latest);
    } catch (IOException e) {
      LOG.warn("Failed to recover CFP submissions from WAL", e);
      return null;
    }
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
    flushDebouncedWritesNow();
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

      if (pendingWrites.isEmpty() && queue.isEmpty() && executor.getActiveCount() == 0 && scheduledRetries.get() == 0) {
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
        "persistence_flush_timeout pendingWrites=%d depth=%d active=%d pendingRetries=%d",
        pendingWrites.size(),
        queue.size(),
        executor.getActiveCount(),
        scheduledRetries.get());
  }

  private Path scheduleFile(int year) {
    return dataDir.resolve(SCHEDULE_FILE_PREFIX + year + SCHEDULE_FILE_SUFFIX);
  }
}
