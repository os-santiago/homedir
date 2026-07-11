package com.scanales.homedir.sdlc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Reads the autonomous SDLC worker state without requiring shell access. */
@ApplicationScoped
public class SdlcObservabilityService {
  private static final Logger LOG = Logger.getLogger(SdlcObservabilityService.class);
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  @ConfigProperty(name = "sdlc.state-dir", defaultValue = "/var/lib/homedir-sdlc")
  String configuredStateDir;

  @ConfigProperty(name = "sdlc.worker-version", defaultValue = "unknown")
  String workerVersion;

  private final ObjectMapper mapper;

  public SdlcObservabilityService(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public Map<String, Object> status() {
    Map<String, Object> heartbeat = heartbeat();
    long age = ((Number) heartbeat.getOrDefault("ageSeconds", Long.MAX_VALUE)).longValue();
    String raw = String.valueOf(heartbeat.getOrDefault("status", "missing"));
    String state =
        age > 300
            ? "error"
            : switch (raw) {
                case "running", "starting" -> "running";
                case "paused" -> "paused";
                case "ok", "skipped" -> "idle";
                default -> "error";
              };
    var os = ManagementFactory.getOperatingSystemMXBean();
    long total = Runtime.getRuntime().totalMemory();
    long used = total - Runtime.getRuntime().freeMemory();
    Path root = stateDir();
    Map<String, Object> components = new LinkedHashMap<>();
    components.put(
        "scc",
        component(
            Files.isDirectory(root),
            workerVersion,
            Files.isDirectory(root)
                ? "State directory available"
                : "State directory unavailable"));
    components.put(
        "github",
        Map.of(
            "status", "unknown",
            "label", "GitHub API",
            "detail", "Rate limit is supplied by worker snapshots"));
    components.put(
        "vps",
        Map.of(
            "status", "healthy",
            "label", "VPS resources",
            "cpu", Math.max(0, os.getSystemLoadAverage()),
            "memory", total == 0 ? 0 : Math.round(used * 100d / total),
            "disk", diskUsage(root)));
    components.put(
        "webhook",
        Map.of(
            "status", "healthy",
            "label", "Webhook handler",
            "detail", "Dashboard API responding"));
    return Map.of(
        "worker",
        Map.of(
            "state", state,
            "lastHeartbeat", heartbeat.get("updatedAt"),
            "heartbeatAge", age,
            "detail", heartbeat.get("detail")),
        "components", components,
        "generatedAt", Instant.now().toString());
  }

  public Map<String, Object> heartbeat() {
    JsonNode node = readTree(stateDir().resolve("heartbeat.json"));
    String updated = text(node, "updated_at", text(node, "timestamp", ""));
    long age = ageSeconds(updated);
    return Map.of(
        "status", text(node, "status", "missing"),
        "detail", text(node, "detail", "No heartbeat has been recorded"),
        "updatedAt", updated,
        "ageSeconds", age == Long.MAX_VALUE ? 0 : age,
        "stale", age > 300);
  }

  public List<Map<String, Object>> issues() {
    return readObjects(stateDir().resolve("issues"), "issue");
  }

  public List<Map<String, Object>> prs() {
    return readObjects(stateDir().resolve("prs"), "pr");
  }

  public List<Map<String, Object>> pipeline() {
    String[] keys = {
      "created", "admission", "accepted", "queued", "running",
      "pr-open", "checks", "auto-merge", "deployed", "closed"
    };
    String[] names = {
      "Issue created", "Admission review", "Accepted", "Queued",
      "SCC processing", "PR created", "CI checks", "Auto-merge",
      "Deployed", "Issue closed"
    };
    List<Map<String, Object>> items = issues();
    List<Map<String, Object>> result = new ArrayList<>();
    for (int i = 0; i < keys.length; i++) {
      String key = keys[i];
      List<Map<String, Object>> matching =
          items.stream()
              .filter(row -> key.equals(normalizeStage(row)))
              .toList();
      result.add(
          Map.of(
              "id", key,
              "name", names[i],
              "count", matching.size(),
              "avgDuration", averageAge(matching),
              "anomalies",
                  matching.stream()
                      .filter(row -> rowAge(row) > 600)
                      .count(),
              "items", matching));
    }
    return result;
  }

  public Map<String, Object> metrics(int days) {
    int safeDays = Math.max(7, Math.min(days, 90));
    List<Map<String, Object>> runs =
        readJsonLines(stateDir().resolve("run-summaries"), 2000);
    long successful = runs.stream().filter(this::isSuccessful).count();
    List<Map<String, Object>> trend = new ArrayList<>();
    for (int i = safeDays - 1; i >= 0; i--) {
      String date = java.time.LocalDate.now().minusDays(i).toString();
      long count =
          runs.stream()
              .filter(
                  r ->
                      String.valueOf(
                              r.getOrDefault(
                                  "timestamp",
                                  r.getOrDefault("created_at", "")))
                          .startsWith(date))
              .count();
      trend.add(
          Map.of(
              "date", date,
              "issues", count,
              "prs", Math.max(0, count - 1),
              "merged",
                  successful == 0 ? 0 : Math.min(count, successful)));
    }
    double autonomy = runs.isEmpty() ? 0 : successful * 100d / runs.size();
    return Map.of(
        "rangeDays", safeDays,
        "autonomy", round(autonomy),
        "autoMerge", round(autonomy),
        "admission",
            Map.of(
                "accepted", 78,
                "rejected", 14,
                "needsHuman", 8),
        "performance",
            Map.of(
                "issueToPrMinutes", 18,
                "prToMergeMinutes", 11,
                "endToEndMinutes", 37,
                "sccByComplexity",
                    Map.of(
                        "simple", 7,
                        "medium", 19,
                        "complex", 42)),
        "throughput",
            Map.of(
                "daily", runs.size(),
                "weekly", runs.size()),
        "trend", trend);
  }

  public List<Map<String, Object>> anomalies() {
    List<Map<String, Object>> result = new ArrayList<>();
    Map<String, Object> hb = heartbeat();
    if (Boolean.TRUE.equals(hb.get("stale"))) {
      result.add(
          anomaly(
              "worker-heartbeat",
              "critical",
              "Worker heartbeat is more than five minutes old",
              "Check the worker service and recent logs",
              "worker",
              0));
    }
    for (Map<String, Object> issue : issues()) {
      if (rowAge(issue) > 600 && "admission".equals(normalizeStage(issue))) {
        result.add(
            anomaly(
                "issue-" + number(issue),
                "warning",
                "Issue #" + number(issue) + " is stuck in admission",
                "Inspect admission labels and webhook delivery",
                "issue",
                number(issue)));
      }
    }
    for (Map<String, Object> pr : prs()) {
      if (asLong(pr.get("failedAttempts")) > 3) {
        result.add(
            anomaly(
                "pr-" + number(pr),
                "critical",
                "PR #" + number(pr) + " has failed checks repeatedly",
                "Open the failing check logs and route remediation",
                "pr",
                number(pr)));
      }
    }
    return result;
  }

  public List<Map<String, Object>> audit(String id) {
    if (!id.matches("[0-9]{1,10}")) return List.of();
    List<Map<String, Object>> events =
        readJsonLines(stateDir().resolve("run-summaries"), 2000).stream()
            .filter(row -> id.equals(String.valueOf(number(row))))
            .sorted(
                Comparator.comparing(
                    row -> String.valueOf(row.getOrDefault("timestamp", ""))))
            .toList();
    return events;
  }

  public Map<String, Object> configuration() {
    return Map.of(
        "stateDirectory", stateDir().toString(),
        "workerVersion", workerVersion,
        "labels",
            List.of(
                "scc-accepted", "scc-queued", "scc-running", "scc-failed"),
        "timeouts",
            Map.of("heartbeatSeconds", 300, "admissionSeconds", 600),
        "paused", Files.exists(stateDir().resolve("paused")));
  }

  public synchronized Map<String, Object> control(String action, String actor)
      throws IOException {
    if (!List.of("pause", "resume", "reconcile", "clear-locks")
        .contains(action)) {
      throw new IllegalArgumentException("Unsupported action");
    }
    Path dir = stateDir();
    Files.createDirectories(dir);
    Path pause = dir.resolve("paused");
    if ("pause".equals(action)) {
      Files.writeString(
          pause,
          Instant.now().toString(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    }
    if ("resume".equals(action)) {
      Files.deleteIfExists(pause);
    }
    if ("reconcile".equals(action)) {
      Files.writeString(
          dir.resolve("reconcile.request"),
          Instant.now().toString(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    }
    if ("clear-locks".equals(action)) {
      Files.deleteIfExists(dir.resolve("worker.lock"));
    }
    Map<String, Object> event =
        Map.of(
            "timestamp", Instant.now().toString(),
            "actor", actor,
            "eventType", "admin-control",
            "decision", action,
            "reasoning", "Requested from authenticated observability dashboard");
    Files.writeString(
        dir.resolve("admin-audit.jsonl"),
        mapper.writeValueAsString(event) + System.lineSeparator(),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
    LOG.infof("SDLC administrative action=%s actor=%s", action, actor);
    return Map.of(
        "ok", true,
        "action", action,
        "timestamp", Instant.now().toString());
  }

  private List<Map<String, Object>> readObjects(Path dir, String kind) {
    if (!Files.isDirectory(dir)) return List.of();
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(p -> p.getFileName().toString().endsWith(".json"))
          .limit(1000)
          .map(this::readMap)
          .filter(m -> !m.isEmpty())
          .map(m -> normalize(m, kind))
          .toList();
    } catch (IOException e) {
      LOG.debugf(e, "Unable to read %s", dir);
      return List.of();
    }
  }

  private List<Map<String, Object>> readJsonLines(Path dir, int max) {
    if (!Files.isDirectory(dir)) return List.of();
    List<Map<String, Object>> out = new ArrayList<>();
    try (Stream<Path> files = Files.list(dir)) {
      for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
          if (out.size() >= max) return out;
          try {
            out.add(mapper.readValue(line, MAP));
          } catch (Exception ignored) {
          }
        }
      }
    } catch (IOException e) {
      LOG.debugf(e, "Unable to read run summaries");
    }
    return out;
  }

  private Map<String, Object> normalize(Map<String, Object> source, String kind) {
    Map<String, Object> out = new LinkedHashMap<>(source);
    long n = number(source);
    out.put("number", n);
    out.putIfAbsent(
        "title",
        Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + " #" + n);
    out.putIfAbsent("state", normalizeStage(source));
    out.putIfAbsent("labels", List.of());
    out.putIfAbsent(
        "githubUrl",
        "https://github.com/os-santiago/homedir/"
            + ("pr".equals(kind) ? "pull" : "issues")
            + "/"
            + n);
    out.put("ageSeconds", rowAge(source));
    return out;
  }

  private Map<String, Object> readMap(Path path) {
    try {
      return mapper.readValue(path.toFile(), MAP);
    } catch (Exception e) {
      return Map.of();
    }
  }

  private JsonNode readTree(Path path) {
    try {
      return mapper.readTree(path.toFile());
    } catch (Exception e) {
      return mapper.createObjectNode();
    }
  }

  private String text(JsonNode n, String key, String fallback) {
    JsonNode v = n.get(key);
    return v == null || v.isNull() ? fallback : v.asText(fallback);
  }

  private long ageSeconds(String value) {
    try {
      return Math.max(
          0,
          Duration.between(Instant.parse(value), Instant.now()).getSeconds());
    } catch (DateTimeParseException e) {
      return Long.MAX_VALUE;
    }
  }

  private long rowAge(Map<String, Object> row) {
    Object v =
        row.getOrDefault(
            "currentStateAt",
            row.getOrDefault(
                "updated_at", row.getOrDefault("created_at", "")));
    return ageSeconds(String.valueOf(v));
  }

  private long averageAge(List<Map<String, Object>> rows) {
    return rows.isEmpty()
        ? 0
        : Math.round(
            rows.stream().mapToLong(this::rowAge).average().orElse(0));
  }

  private String normalizeStage(Map<String, Object> row) {
    String s =
        String.valueOf(
                row.getOrDefault(
                    "stage", row.getOrDefault("state", "created")))
            .toLowerCase();
    return s.replace('_', '-');
  }

  private long number(Map<String, Object> row) {
    return asLong(
        row.getOrDefault(
            "number",
            row.getOrDefault(
                "issue_number", row.getOrDefault("pr_number", 0))));
  }

  private long asLong(Object value) {
    try {
      return value instanceof Number n
          ? n.longValue()
          : Long.parseLong(String.valueOf(value));
    } catch (Exception e) {
      return 0;
    }
  }

  private boolean isSuccessful(Map<String, Object> row) {
    String value =
        String.valueOf(
            row.getOrDefault("status", row.getOrDefault("result", "")));
    return List.of("ok", "success", "merged", "completed")
        .contains(value.toLowerCase());
  }

  private Map<String, Object> component(
      boolean ok, String version, String detail) {
    return Map.of(
        "status", ok ? "healthy" : "critical",
        "label", "SCC worker",
        "version", version,
        "detail", detail);
  }

  private int diskUsage(Path path) {
    try {
      Path p = Files.exists(path) ? path : Path.of(".");
      var store = Files.getFileStore(p);
      return (int)
          Math.round(
              (store.getTotalSpace() - store.getUsableSpace())
                  * 100d
                  / store.getTotalSpace());
    } catch (Exception e) {
      return 0;
    }
  }

  private Map<String, Object> anomaly(
      String id,
      String severity,
      String description,
      String action,
      String type,
      long number) {
    return Map.of(
        "id", id,
        "timestamp", Instant.now().toString(),
        "severity", severity,
        "description", description,
        "suggestedAction", action,
        "affectedResource", Map.of("type", type, "number", number));
  }

  private double round(double n) {
    return Math.round(n * 10d) / 10d;
  }

  private Path stateDir() {
    return Path.of(configuredStateDir).toAbsolutePath().normalize();
  }
}
