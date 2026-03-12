package com.scanales.eventflow.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Stores low-cardinality hourly activity aggregates so product/admin dashboards can explain where
 * users are active without shipping raw clickstream infrastructure.
 */
@ApplicationScoped
public class BusinessObservabilityLedgerService {

  private static final Logger LOG = Logger.getLogger(BusinessObservabilityLedgerService.class);
  private static final int CURRENT_SCHEMA_VERSION = 1;
  private static final DateTimeFormatter HOUR_LABEL =
      DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

  private final Map<String, Long> moduleHourly = new ConcurrentHashMap<>();
  private final Map<String, Long> actionHourly = new ConcurrentHashMap<>();
  private final Map<String, Long> moduleLastSeen = new ConcurrentHashMap<>();
  private final Map<String, Long> actionLastSeen = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final AtomicBoolean dirty = new AtomicBoolean(false);

  @Inject ObjectMapper objectMapper;

  @ConfigProperty(name = "observability.business.enabled", defaultValue = "true")
  boolean enabled;

  @ConfigProperty(name = "observability.business.flush-interval", defaultValue = "PT30S")
  Duration flushInterval;

  @ConfigProperty(name = "observability.business.retention-hours", defaultValue = "336")
  int retentionHours;

  @ConfigProperty(
      name = "observability.business.file",
      defaultValue = "observability/business-observability-v1.json")
  String relativePath;

  @ConfigProperty(name = "homedir.data.dir", defaultValue = "data")
  String dataDir;

  private Path storagePath;
  private ObjectMapper mapper;

  @PostConstruct
  void init() {
    mapper = objectMapper.copy();
    if (retentionHours < 24) {
      retentionHours = 24;
    }
    String sysProp = System.getProperty("homedir.data.dir");
    if (sysProp != null && !sysProp.isBlank()) {
      dataDir = sysProp;
    }
    storagePath = Paths.get(dataDir).resolve(relativePath).normalize();
    if (!enabled) {
      LOG.info("business_observability_disabled");
      return;
    }
    load();
    scheduler.scheduleWithFixedDelay(
        this::flushSafe, flushInterval.toMillis(), flushInterval.toMillis(), TimeUnit.MILLISECONDS);
    Runtime.getRuntime().addShutdownHook(new Thread(this::flushSafe));
  }

  @PreDestroy
  void shutdown() {
    flushSafe();
    scheduler.shutdown();
  }

  public void recordRoute(String route) {
    recordModule(BusinessObservabilityTaxonomy.moduleForRoute(route));
  }

  public void recordModule(String module) {
    if (!enabled) {
      return;
    }
    recordSeries(moduleHourly, moduleLastSeen, sanitizeCode(module), Instant.now());
  }

  public void recordAction(String rawAction) {
    if (!enabled) {
      return;
    }
    String canonical = BusinessObservabilityTaxonomy.canonicalAction(rawAction);
    if (canonical == null) {
      return;
    }
    recordSeries(actionHourly, actionLastSeen, sanitizeCode(canonical), Instant.now());
  }

  public ObservabilityWindow window(int hours) {
    int safeHours = Math.max(6, Math.min(hours, retentionHours));
    long latestEpochHour = currentEpochHour(Instant.now());
    long firstEpochHour = latestEpochHour - safeHours + 1L;
    long previousFirstEpochHour = firstEpochHour - safeHours;
    List<String> hourLabels = new ArrayList<>(safeHours);
    for (long bucket = firstEpochHour; bucket <= latestEpochHour; bucket++) {
      hourLabels.add(HOUR_LABEL.format(Instant.ofEpochSecond(bucket * 3600L)));
    }

    List<SeriesSnapshot> modules =
        buildSeries(
            BusinessObservabilityTaxonomy.moduleOrder(),
            moduleHourly,
            moduleLastSeen,
            firstEpochHour,
            latestEpochHour,
            previousFirstEpochHour);
    List<SeriesSnapshot> actions =
        buildSeries(
            BusinessObservabilityTaxonomy.actionOrder(),
            actionHourly,
            actionLastSeen,
            firstEpochHour,
            latestEpochHour,
            previousFirstEpochHour);

    long interactionsLastWindow = modules.stream().mapToLong(SeriesSnapshot::total).sum();
    long interactionsPreviousWindow = modules.stream().mapToLong(SeriesSnapshot::previousTotal).sum();
    return new ObservabilityWindow(
        System.currentTimeMillis(),
        safeHours,
        hourLabels,
        modules,
        actions,
        interactionsLastWindow,
        interactionsPreviousWindow);
  }

  public void reset() {
    moduleHourly.clear();
    actionHourly.clear();
    moduleLastSeen.clear();
    actionLastSeen.clear();
    dirty.set(false);
    try {
      if (storagePath != null) {
        Files.deleteIfExists(storagePath);
      }
    } catch (IOException ignored) {
    }
  }

  public record SeriesSnapshot(
      String code, List<Long> counts, long total, long previousTotal, Long trendPct, Long lastSeenAt) {}

  public record ObservabilityWindow(
      long generatedAtMillis,
      int windowHours,
      List<String> hourLabels,
      List<SeriesSnapshot> modules,
      List<SeriesSnapshot> actions,
      long interactionsLastWindow,
      long interactionsPreviousWindow) {}

  private void load() {
    try {
      Path parent = storagePath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      if (!Files.exists(storagePath)) {
        return;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> raw = mapper.readValue(storagePath.toFile(), Map.class);
      restoreMap(raw.get("moduleHourly"), moduleHourly);
      restoreMap(raw.get("actionHourly"), actionHourly);
      restoreMap(raw.get("moduleLastSeen"), moduleLastSeen);
      restoreMap(raw.get("actionLastSeen"), actionLastSeen);
      pruneOldBuckets(currentEpochHour(Instant.now()));
    } catch (Exception e) {
      LOG.warn("business_observability_load_failed", e);
    }
  }

  private void restoreMap(Object raw, Map<String, Long> target) {
    if (!(raw instanceof Map<?, ?> values)) {
      return;
    }
    values.forEach(
        (key, value) -> {
          if (key != null && value instanceof Number number) {
            target.put(String.valueOf(key), number.longValue());
          }
        });
  }

  private void recordSeries(
      Map<String, Long> buckets, Map<String, Long> lastSeenMap, String code, Instant now) {
    if (code == null) {
      return;
    }
    long epochHour = currentEpochHour(now);
    buckets.merge(bucketKey(code, epochHour), 1L, Long::sum);
    lastSeenMap.put(code, now.toEpochMilli());
    pruneOldBuckets(epochHour);
    dirty.set(true);
  }

  private List<SeriesSnapshot> buildSeries(
      List<String> codes,
      Map<String, Long> buckets,
      Map<String, Long> lastSeenMap,
      long firstEpochHour,
      long latestEpochHour,
      long previousFirstEpochHour) {
    List<SeriesSnapshot> rows = new ArrayList<>();
    for (String code : codes) {
      List<Long> counts = new ArrayList<>((int) (latestEpochHour - firstEpochHour + 1));
      long total = 0L;
      for (long hour = firstEpochHour; hour <= latestEpochHour; hour++) {
        long value = buckets.getOrDefault(bucketKey(code, hour), 0L);
        counts.add(value);
        total += value;
      }
      long previousTotal = 0L;
      for (long hour = previousFirstEpochHour; hour < firstEpochHour; hour++) {
        previousTotal += buckets.getOrDefault(bucketKey(code, hour), 0L);
      }
      if (total <= 0L && previousTotal <= 0L) {
        continue;
      }
      rows.add(
          new SeriesSnapshot(
              code,
              List.copyOf(counts),
              total,
              previousTotal,
              trendPct(total, previousTotal),
              lastSeenMap.get(code)));
    }
    rows.sort(Comparator.comparingLong(SeriesSnapshot::total).reversed().thenComparing(SeriesSnapshot::code));
    return rows;
  }

  private Long trendPct(long current, long previous) {
    if (previous <= 0L) {
      return current > 0L ? 100L : null;
    }
    return Math.round(((double) (current - previous) / (double) previous) * 100d);
  }

  private void pruneOldBuckets(long latestEpochHour) {
    long minEpochHour = latestEpochHour - retentionHours;
    pruneMap(moduleHourly, minEpochHour);
    pruneMap(actionHourly, minEpochHour);
  }

  private void pruneMap(Map<String, Long> buckets, long minEpochHour) {
    buckets.keySet().removeIf(key -> parseEpochHour(key) < minEpochHour);
  }

  private long parseEpochHour(String key) {
    int pipe = key.lastIndexOf('|');
    if (pipe < 0 || pipe + 1 >= key.length()) {
      return Long.MIN_VALUE;
    }
    try {
      return Long.parseLong(key.substring(pipe + 1));
    } catch (NumberFormatException ignored) {
      return Long.MIN_VALUE;
    }
  }

  private long currentEpochHour(Instant instant) {
    return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)
        .toEpochSecond()
        / 3600L;
  }

  private String bucketKey(String code, long epochHour) {
    return code + "|" + epochHour;
  }

  private String sanitizeCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    String normalized = code.trim().toLowerCase();
    StringBuilder safe = new StringBuilder(normalized.length());
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
        safe.append(c);
      }
    }
    if (safe.isEmpty() || safe.length() > 64) {
      return null;
    }
    return safe.toString();
  }

  private void flushSafe() {
    if (!enabled || !dirty.get()) {
      return;
    }
    try {
      Path parent = storagePath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("schemaVersion", CURRENT_SCHEMA_VERSION);
      payload.put("generatedAt", Instant.now().toString());
      payload.put("moduleHourly", new LinkedHashMap<>(moduleHourly));
      payload.put("actionHourly", new LinkedHashMap<>(actionHourly));
      payload.put("moduleLastSeen", new LinkedHashMap<>(moduleLastSeen));
      payload.put("actionLastSeen", new LinkedHashMap<>(actionLastSeen));
      Path tempPath = storagePath.resolveSibling(storagePath.getFileName() + ".tmp");
      mapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), payload);
      Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      dirty.set(false);
    } catch (Exception e) {
      LOG.warn("business_observability_flush_failed", e);
    }
  }
}
