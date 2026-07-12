package com.scanales.homedir.sdlc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Incrementally consumes the worker's existing append-only event journal.
 *
 * <p>The worker never calls this component and never waits for it. Each cycle has hard file, byte,
 * event, and memory limits. HTTP readers only dereference an immutable in-memory projection.
 */
@ApplicationScoped
public class SdlcDashboardSnapshot {
  private static final Logger LOG = Logger.getLogger(SdlcDashboardSnapshot.class);
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
  private static final String[] STAGES = {
    "created", "admission", "accepted", "queued", "running",
    "pr-open", "checks", "auto-merge", "deployed", "closed"
  };
  private static final String[] STAGE_NAMES = {
    "Issue created", "Admission review", "Accepted", "Queued", "SCC processing",
    "PR created", "CI checks", "Auto-merge", "Deployed", "Issue closed"
  };

  private final ObjectMapper mapper;
  private final Map<Path, Long> offsets = new HashMap<>();
  private final Deque<Map<String, Object>> events = new ArrayDeque<>();
  private final AtomicReference<Map<String, Object>> current = new AtomicReference<>(empty());

  @ConfigProperty(name = "sdlc.state-dir", defaultValue = "/var/lib/homedir-sdlc")
  String stateDir;

  @ConfigProperty(name = "sdlc.worker-version", defaultValue = "unknown")
  String workerVersion;

  @ConfigProperty(name = "sdlc.dashboard.controls-enabled", defaultValue = "false")
  boolean controlsEnabled;

  @ConfigProperty(name = "sdlc.dashboard.max-events", defaultValue = "2000")
  int maxEvents;

  @ConfigProperty(name = "sdlc.dashboard.max-files-per-cycle", defaultValue = "250")
  int maxFilesPerCycle;

  @ConfigProperty(name = "sdlc.dashboard.max-bytes-per-cycle", defaultValue = "65536")
  long maxBytesPerCycle;

  private int failures;
  private Instant retryAfter = Instant.EPOCH;

  public SdlcDashboardSnapshot(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Scheduled(
      every = "${sdlc.dashboard.snapshot-interval:30s}",
      delayed = "5s",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void refresh() {
    if (Instant.now().isBefore(retryAfter)) return;
    try {
      ingestBounded();
      current.set(project(false));
      failures = 0;
      retryAfter = Instant.EPOCH;
    } catch (RuntimeException e) {
      failures = Math.min(failures + 1, 6);
      retryAfter = Instant.now().plusSeconds(Math.min(300, 1L << failures));
      LOG.warnf("SDLC event ingestion backed off until %s: %s", retryAfter, e.getMessage());
      current.updateAndGet(this::markStale);
    }
  }

  public Map<String, Object> get() {
    return current.get();
  }

  public List<Map<String, Object>> audit(String id) {
    return events.stream()
        .filter(
            event ->
                id.equals(String.valueOf(number(event, "issue")))
                    || id.equals(String.valueOf(number(event, "pr_number"))))
        .sorted(Comparator.comparing(event -> String.valueOf(event.getOrDefault("created_at", ""))))
        .toList();
  }

  private synchronized void ingestBounded() {
    Path journal = Path.of(stateDir).toAbsolutePath().normalize().resolve("run-summaries");
    if (!Files.isDirectory(journal)) {
      current.set(empty());
      return;
    }
    long remaining = Math.max(1024, maxBytesPerCycle);
    try (Stream<Path> paths = Files.list(journal)) {
      for (Path path :
          paths
              .filter(Files::isRegularFile)
              .sorted(Comparator.comparingLong(this::lastModified).reversed())
              .limit(Math.max(1, maxFilesPerCycle))
              .toList()) {
        if (remaining <= 0) break;
        remaining -= tail(path, remaining);
      }
    } catch (Exception e) {
      throw new IllegalStateException("event journal unavailable", e);
    }
    while (events.size() > Math.max(100, maxEvents)) events.removeFirst();
  }

  private long tail(Path path, long allowance) {
    long consumed = 0;
    try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
      long previous = offsets.getOrDefault(path, 0L);
      long offset = previous > file.length() ? 0 : previous;
      file.seek(offset);
      while (consumed < allowance) {
        long lineStart = file.getFilePointer();
        String encoded = file.readLine();
        if (encoded == null) break;
        long lineBytes = file.getFilePointer() - lineStart;
        if (consumed + lineBytes > allowance) {
          file.seek(lineStart);
          break;
        }
        consumed += lineBytes;
        String line =
            new String(encoded.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        try {
          events.addLast(mapper.readValue(line, MAP));
        } catch (Exception ignored) {
          // A malformed producer line is isolated and cannot stop subsequent telemetry.
        }
      }
      offsets.put(path, file.getFilePointer());
      return consumed;
    } catch (Exception e) {
      LOG.debugf(e, "Skipping unreadable SDLC event file %s", path);
      return 0;
    }
  }

  private Map<String, Object> project(boolean stale) {
    Map<Long, Map<String, Object>> issues = new LinkedHashMap<>();
    Map<Long, Map<String, Object>> prs = new LinkedHashMap<>();
    Instant newest = Instant.EPOCH;
    for (Map<String, Object> event : events) {
      long issue = number(event, "issue");
      long pr = number(event, "pr_number");
      String timestamp = String.valueOf(event.getOrDefault("created_at", ""));
      Instant at = parse(timestamp);
      if (at.isAfter(newest)) newest = at;
      if (issue > 0) issues.put(issue, issueProjection(event, issue, at));
      if (pr > 0) prs.put(pr, prProjection(event, pr, at));
    }
    String workerState =
        newest.equals(Instant.EPOCH)
            ? "unknown"
            : Duration.between(newest, Instant.now()).toMinutes() > 10 ? "idle" : "running";
    long age =
        newest.equals(Instant.EPOCH)
            ? 0
            : Math.max(0, Duration.between(newest, Instant.now()).toSeconds());
    List<Map<String, Object>> issueList = new ArrayList<>(issues.values());
    List<Map<String, Object>> prList = new ArrayList<>(prs.values());
    Map<String, Object> status =
        Map.of(
            "worker",
                Map.of(
                    "state",
                    workerState,
                    "lastHeartbeat",
                    newest.toString(),
                    "heartbeatAge",
                    age,
                    "detail",
                    "Derived asynchronously from the append-only event journal"),
            "components",
                Map.of(
                    "eventJournal",
                        Map.of(
                            "status",
                            stale ? "warning" : "healthy",
                            "label",
                            "Event journal",
                            "detail",
                            events.size() + " bounded events in memory"),
                    "observer",
                        Map.of(
                            "status",
                            "healthy",
                            "label",
                            "Observer isolation",
                            "detail",
                            "No worker-path callbacks or HTTP file I/O")),
            "generatedAt", Instant.now().toString());
    Map<String, Object> metrics7 = metrics(7);
    Map<String, Object> metrics30 = metrics(30);
    Map<String, Object> metrics90 = metrics(90);
    return Map.ofEntries(
        Map.entry("status", status),
        Map.entry("pipeline", pipeline(issueList)),
        Map.entry("issues", issueList),
        Map.entry("prs", prList),
        Map.entry("metrics", metrics30),
        Map.entry("metricsByRange", Map.of("7", metrics7, "30", metrics30, "90", metrics90)),
        Map.entry("anomalies", anomalies(issueList, newest)),
        Map.entry("configuration", configuration()),
        Map.entry("generatedAt", Instant.now().toString()),
        Map.entry("stale", stale));
  }

  private Map<String, Object> issueProjection(Map<String, Object> event, long issue, Instant at) {
    String stage = stage(String.valueOf(event.getOrDefault("event", "created")));
    return Map.of(
        "number",
        issue,
        "title",
        "Issue #" + issue,
        "state",
        stage,
        "labels",
        List.of(),
        "ageSeconds",
        Math.max(0, Duration.between(at, Instant.now()).toSeconds()),
        "githubUrl",
        "https://github.com/os-santiago/homedir/issues/" + issue);
  }

  private Map<String, Object> prProjection(Map<String, Object> event, long pr, Instant at) {
    return Map.of(
        "number",
        pr,
        "title",
        "Pull request #" + pr,
        "state",
        stage(String.valueOf(event.getOrDefault("event", "pr-opened"))),
        "checksStatus",
        "unknown",
        "autoMerge",
        false,
        "ageSeconds",
        Math.max(0, Duration.between(at, Instant.now()).toSeconds()),
        "githubUrl",
        "https://github.com/os-santiago/homedir/pull/" + pr);
  }

  private List<Map<String, Object>> pipeline(List<Map<String, Object>> issues) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (int i = 0; i < STAGES.length; i++) {
      String id = STAGES[i];
      List<Map<String, Object>> matching =
          issues.stream().filter(row -> id.equals(row.get("state"))).toList();
      result.add(
          Map.of(
              "id",
              id,
              "name",
              STAGE_NAMES[i],
              "count",
              matching.size(),
              "avgDuration",
              averageAge(matching),
              "anomalies",
              0,
              "items",
              matching));
    }
    return result;
  }

  private Map<String, Object> metrics(int days) {
    List<Map<String, Object>> trend = new ArrayList<>();
    long successful =
        events.stream()
            .filter(event -> stage(String.valueOf(event.get("event"))).equals("closed"))
            .count();
    for (int i = days - 1; i >= 0; i--) {
      String date = LocalDate.now().minusDays(i).toString();
      long count =
          events.stream()
              .filter(
                  event -> String.valueOf(event.getOrDefault("created_at", "")).startsWith(date))
              .count();
      trend.add(
          Map.of(
              "date", date, "issues", count, "prs", count, "merged", Math.min(count, successful)));
    }
    double autonomy = events.isEmpty() ? 0 : Math.round(successful * 1000d / events.size()) / 10d;
    return Map.of(
        "rangeDays",
        days,
        "autonomy",
        autonomy,
        "autoMerge",
        autonomy,
        "admission",
        Map.of("accepted", 0, "rejected", 0, "needsHuman", 0),
        "performance",
        Map.of(
            "issueToPrMinutes",
            0,
            "prToMergeMinutes",
            0,
            "endToEndMinutes",
            0,
            "sccByComplexity",
            Map.of("simple", 0, "medium", 0, "complex", 0)),
        "throughput",
        Map.of("daily", events.size(), "weekly", events.size()),
        "trend",
        trend);
  }

  private List<Map<String, Object>> anomalies(List<Map<String, Object>> issues, Instant newest) {
    if (!newest.equals(Instant.EPOCH) && Duration.between(newest, Instant.now()).toMinutes() <= 15)
      return List.of();
    return List.of(
        Map.of(
            "id",
            "event-journal-stale",
            "timestamp",
            Instant.now().toString(),
            "severity",
            "warning",
            "description",
            "No recent autonomous SDLC events",
            "suggestedAction",
            "Check the worker timer only if queued work exists",
            "affectedResource",
            Map.of("type", "worker", "number", 0)));
  }

  private Map<String, Object> configuration() {
    return Map.of(
        "stateDirectory",
        "event-journal",
        "workerVersion",
        workerVersion,
        "labels",
        List.of(),
        "timeouts",
        Map.of("snapshotSeconds", 30),
        "paused",
        false,
        "controlsEnabled",
        controlsEnabled,
        "maxEvents",
        maxEvents,
        "maxFilesPerCycle",
        maxFilesPerCycle,
        "maxBytesPerCycle",
        maxBytesPerCycle);
  }

  private Map<String, Object> markStale(Map<String, Object> previous) {
    Map<String, Object> copy = new LinkedHashMap<>(previous);
    copy.put("stale", true);
    return Map.copyOf(copy);
  }

  private static Map<String, Object> empty() {
    return Map.ofEntries(
        Map.entry(
            "status",
            Map.of(
                "worker",
                Map.of("state", "unknown", "heartbeatAge", 0, "detail", "Waiting for events"),
                "components",
                Map.of(),
                "generatedAt",
                Instant.now().toString())),
        Map.entry("pipeline", List.of()),
        Map.entry("issues", List.of()),
        Map.entry("prs", List.of()),
        Map.entry("metrics", Map.of()),
        Map.entry("metricsByRange", Map.of()),
        Map.entry("anomalies", List.of()),
        Map.entry(
            "configuration",
            Map.of(
                "workerVersion",
                "unknown",
                "stateDirectory",
                "event-journal",
                "labels",
                List.of(),
                "timeouts",
                Map.of(),
                "paused",
                false,
                "controlsEnabled",
                false)),
        Map.entry("generatedAt", Instant.now().toString()),
        Map.entry("stale", true));
  }

  private String stage(String event) {
    String value = event == null ? "" : event.toLowerCase();
    if (value.contains("deploy") || value.contains("merged")) return "deployed";
    if (value.contains("closed") || value.contains("complete")) return "closed";
    if (value.contains("check")) return "checks";
    if (value.contains("pr-open")) return "pr-open";
    if (value.contains("running") || value.contains("scc")) return "running";
    if (value.contains("queue")) return "queued";
    if (value.contains("accept")) return "accepted";
    if (value.contains("admission")) return "admission";
    return "created";
  }

  private long averageAge(List<Map<String, Object>> rows) {
    return rows.isEmpty()
        ? 0
        : Math.round(rows.stream().mapToLong(row -> number(row, "ageSeconds")).average().orElse(0));
  }

  private long number(Map<String, Object> row, String key) {
    Object value = row.get(key);
    try {
      return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    } catch (Exception ignored) {
      return 0;
    }
  }

  private Instant parse(String value) {
    try {
      return Instant.parse(value);
    } catch (Exception ignored) {
      return Instant.EPOCH;
    }
  }

  private long lastModified(Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    } catch (Exception ignored) {
      return 0;
    }
  }
}
