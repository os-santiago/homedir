package com.scanales.homedir.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.homedir.model.Speaker;
import com.scanales.homedir.observability.BusinessObservabilityTaxonomy;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** Tracks simple usage metrics in memory with asynchronous persistence. */
@ApplicationScoped
public class UsageMetricsService {

  private static final Logger LOG = Logger.getLogger(UsageMetricsService.class);
  private static final int CURRENT_SCHEMA_VERSION = 3;

  private final Map<String, Long> counters = new ConcurrentHashMap<>();
  private final Map<String, Long> talkViews = new ConcurrentHashMap<>();
  private final Map<String, Long> eventViews = new ConcurrentHashMap<>();
  private final Map<String, Long> stageVisits = new ConcurrentHashMap<>();
  private final Map<String, Long> pageViews = new ConcurrentHashMap<>();
  private final Map<String, Long> moduleHourly = new ConcurrentHashMap<>();
  private final Map<String, Long> actionHourly = new ConcurrentHashMap<>();
  private final Map<String, Long> moduleLastSeen = new ConcurrentHashMap<>();
  private final Map<String, Long> actionLastSeen = new ConcurrentHashMap<>();
  private final Map<String, RateLimiter> rates = new ConcurrentHashMap<>();
  private final Map<String, LongAdder> discardedByReason = new ConcurrentHashMap<>();
  private final Map<String, Set<Registrant>> registrations = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final AtomicBoolean dirty = new AtomicBoolean(false);
  private final AtomicLong dirtySinceTime = new AtomicLong();
  private final AtomicLong flushFailures = new AtomicLong();
  private final AtomicInteger bufferSize = new AtomicInteger();
  private final AtomicLong lastBufferFullWarnAt = new AtomicLong();
  private final AtomicLong writesOk = new AtomicLong();
  private final AtomicLong writesFail = new AtomicLong();
  private volatile long lastFlushTime;
  private volatile long lastHealthTransitionLogTime;
  private volatile String lastError;
  private volatile HealthState currentState = HealthState.OK;
  private volatile boolean bufferWarned;

  private Path metricsV1Path;
  private Path metricsV2Path;
  private Path businessObservabilityV1Path;
  private Path metricsPath;
  private boolean migrateFromV1;
  private boolean migrateObservabilityV1;
  private int schemaVersion = CURRENT_SCHEMA_VERSION;
  private long lastFileSizeBytes;

  @RegisterForReflection
  public record Registrant(String name, String email) {
  }

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

  @ConfigProperty(name = "metrics.buffer.max-size", defaultValue = "10000")
  int bufferMaxSize;

  @ConfigProperty(name = "metrics.health.log-cooldown", defaultValue = "PT60S")
  Duration healthLogCooldown;

  @ConfigProperty(name = "metrics.log.buffer-full-cooldown", defaultValue = "PT30S")
  Duration bufferFullLogCooldown;

  @ConfigProperty(name = "observability.business.retention-hours", defaultValue = "336")
  int observabilityRetentionHours;

  @ConfigProperty(name = "homedir.data.dir", defaultValue = "data")
  String dataDir = "data";

  @Inject ObjectMapper mapper;

  @PostConstruct
  void init() {
    String sysProp = System.getProperty("homedir.data.dir");
    if (sysProp != null && !sysProp.isBlank()) {
      dataDir = sysProp;
    }
    this.metricsV1Path = Paths.get(dataDir, "metrics-v1.json");
    this.metricsV2Path = Paths.get(dataDir, "metrics-v2.json");
    this.businessObservabilityV1Path =
        Paths.get(dataDir, "observability", "business-observability-v1.json");
    load();
    lastFlushTime = System.currentTimeMillis();
    scheduler.scheduleWithFixedDelay(
        this::flushSafe, flushInterval.toMillis(), flushInterval.toMillis(), TimeUnit.MILLISECONDS);
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
      if (Files.exists(metricsV2Path)) {
        metricsPath = metricsV2Path;
      } else if (Files.exists(metricsV1Path)) {
        metricsPath = metricsV1Path;
        migrateFromV1 = true;
      } else {
        metricsPath = metricsV2Path;
      }
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
            dm.forEach(
                (k, v) -> discardedByReason
                    .computeIfAbsent(String.valueOf(k), r -> new LongAdder())
                    .add(((Number) v).longValue()));
          }
          Object sv = meta.get("schemaVersion");
          if (sv instanceof Number n) {
            schemaVersion = n.intValue();
          }
          Object fs = meta.get("fileSizeBytes");
          if (fs instanceof Number n) {
            lastFileSizeBytes = n.longValue();
          }
          Object lf = meta.get("lastFlush");
          if (lf instanceof Number n) {
            lastFlushTime = n.longValue();
          }
        }
        Object regsObj = data.get("registrants");
        if (regsObj instanceof Map<?, ?> rm) {
          rm.forEach(
              (k, v) -> {
                String talkId = String.valueOf(k);
                Set<Registrant> set = ConcurrentHashMap.newKeySet();
                if (v instanceof java.util.List<?> list) {
                  for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                      Object nObj = m.get("name");
                      Object eObj = m.get("email");
                      if (nObj instanceof String n && eObj instanceof String e) {
                        set.add(new Registrant(n, e));
                      }
                    }
                  }
                }
                registrations.put(talkId, set);
              });
        }
        restoreObservability(data.get("observability"));
      }
      if (moduleHourly.isEmpty()
          && actionHourly.isEmpty()
          && Files.exists(businessObservabilityV1Path)) {
        importLegacyObservabilityLedger();
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

  public record ObservabilitySeriesSnapshot(
      String code, List<Long> counts, long total, long previousTotal, Long trendPct, Long lastSeenAt) {}

  public record ObservabilityWindow(
      long generatedAtMillis,
      int windowHours,
      List<String> hourLabels,
      List<ObservabilitySeriesSnapshot> modules,
      List<ObservabilitySeriesSnapshot> actions,
      long interactionsLastWindow,
      long interactionsPreviousWindow) {}

  public Summary getSummary() {
    try {
      byte[] json = mapper.writeValueAsBytes(counters);
      return new Summary(counters.size(), json.length, getDiscarded());
    } catch (Exception e) {
      return new Summary(counters.size(), 0L, getDiscarded());
    }
  }

  /** Current schema version loaded in memory. */
  public int getSchemaVersion() {
    return schemaVersion;
  }

  /** Size in bytes of the last flushed metrics file. */
  public long getFileSizeBytes() {
    return lastFileSizeBytes;
  }

  public ObservabilityWindow observabilityWindow(int hours) {
    int retention = Math.max(24, observabilityRetentionHours);
    int safeHours = Math.max(6, Math.min(hours, retention));
    long latestEpochHour = currentEpochHour(Instant.now());
    long firstEpochHour = latestEpochHour - safeHours + 1L;
    long previousFirstEpochHour = firstEpochHour - safeHours;
    List<String> hourLabels = new ArrayList<>(safeHours);
    for (long bucket = firstEpochHour; bucket <= latestEpochHour; bucket++) {
      hourLabels.add(
          java.time.format.DateTimeFormatter.ofPattern("HH:mm")
              .withZone(ZoneOffset.UTC)
              .format(Instant.ofEpochSecond(bucket * 3600L)));
    }
    List<ObservabilitySeriesSnapshot> modules =
        buildObservabilitySeries(
            BusinessObservabilityTaxonomy.moduleOrder(),
            moduleHourly,
            moduleLastSeen,
            firstEpochHour,
            latestEpochHour,
            previousFirstEpochHour);
    List<ObservabilitySeriesSnapshot> actions =
        buildObservabilitySeries(
            BusinessObservabilityTaxonomy.actionOrder(),
            actionHourly,
            actionLastSeen,
            firstEpochHour,
            latestEpochHour,
            previousFirstEpochHour);
    long interactionsLastWindow = modules.stream().mapToLong(ObservabilitySeriesSnapshot::total).sum();
    long interactionsPreviousWindow =
        modules.stream().mapToLong(ObservabilitySeriesSnapshot::previousTotal).sum();
    return new ObservabilityWindow(
        System.currentTimeMillis(),
        safeHours,
        List.copyOf(hourLabels),
        modules,
        actions,
        interactionsLastWindow,
        interactionsPreviousWindow);
  }

  private boolean isBot(String ua) {
    if (ua == null)
      return false;
    String u = ua.toLowerCase();
    return u.contains("bot")
        || u.contains("spider")
        || u.contains("crawl")
        || u.contains("headless")
        || u.contains("phantomjs")
        || u.contains("selenium")
        || u.contains("puppeteer");
  }

  public void recordPageView(String route, String ua) {
    recordPageView(route, null, ua);
  }

  public void recordPageView(String route, HttpHeaders headers, RoutingContext context) {
    String ua = headers.getHeaderString("User-Agent");
    String sessionId = context.session() != null ? context.session().id() : null;
    recordPageView(route, sessionId, ua);
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
    recordObservabilityRoute(route);
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
    recordObservabilityModule("events");
    recordObservabilityAction("event_view");
  }

  public void recordTalkView(String talkId, String sessionId, String ua) {
    if (talkId == null) {
      incrementDiscard("invalid");
      return;
    }
    if (isBot(ua)) {
      incrementDiscard("bot");
      return;
    }
    if (sessionId != null) {
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
    }
    increment("talk_view:" + talkId);
    recordObservabilityModule("events");
    recordObservabilityAction("talk_view");
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
    recordObservabilityModule("events");
    recordObservabilityAction("talk_register");
  }

  public void recordTalkRegister(
      String talkId, java.util.List<Speaker> speakers, String ua, String name, String email) {
    recordTalkRegister(talkId, speakers, ua);
    if (talkId == null || isBot(ua) || name == null || email == null) {
      return;
    }
    registrations
        .computeIfAbsent(talkId, k -> ConcurrentHashMap.newKeySet())
        .add(new Registrant(name, email));
    markDirty(System.currentTimeMillis());
  }

  /** Removes a talk registration and decrements its counter if present. */
  public void recordTalkUnregister(String talkId, String email) {
    if (talkId == null || email == null) {
      return;
    }
    Set<Registrant> regs = registrations.get(talkId);
    if (regs == null) {
      return;
    }
    Registrant target = null;
    for (Registrant r : regs) {
      if (email.equals(r.email())) {
        target = r;
        break;
      }
    }
    if (target != null) {
      regs.remove(target);
      decrement("talk_register:" + talkId);
      if (regs.isEmpty()) {
        registrations.remove(talkId);
      }
    }
  }

  public void recordStageVisit(String stageId, String timezone, String sessionId, String ua) {
    if (stageId == null) {
      incrementDiscard("invalid");
      return;
    }
    if (isBot(ua)) {
      incrementDiscard("bot");
      return;
    }
    // Metrics are tracked using a fixed timezone so that admin filters like
    // "today" work regardless of the event's timezone. Using a consistent
    // zone avoids gaps when events are recorded in different regions.
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    if (sessionId != null) {
      if (isBurst(sessionId)) {
        incrementDiscard("burst");
        return;
      }
      String key = sessionId + ":" + stageId + ":" + today;
      long now = System.currentTimeMillis();
      Long last = stageVisits.put(key, now);
      if (last != null && now - last < stageVisitWindow.toMillis()) {
        incrementDiscard("dedupe");
        return;
      }
    }
    increment("stage_visit:" + stageId + ":" + today);
    recordObservabilityModule("events");
    recordObservabilityAction("stage_visit");
  }

  public void recordStageVisit(
      String stageId, String timezone, HttpHeaders headers, RoutingContext context) {
    String ua = headers.getHeaderString("User-Agent");
    String sessionId = context.session() != null ? context.session().id() : null;
    recordStageVisit(stageId, timezone, sessionId, ua);
  }

  public List<Registrant> getRegistrants(String talkId) {
    return registrations.containsKey(talkId)
        ? new ArrayList<>(registrations.get(talkId))
        : List.of();
  }

  public Map<String, List<Registrant>> getRegistrations() {
    Map<String, List<Registrant>> copy = new HashMap<>();
    registrations.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
    return copy;
  }

  public void recordCta(String name, String timezone) {
    if (name == null) {
      incrementDiscard("invalid");
      return;
    }
    // Use the same fixed timezone as stage visits for consistency.
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    increment("cta:" + name + ":" + today);
  }

  /** Records a product funnel step that can be reported in admin metrics. */
  public void recordFunnelStep(String step) {
    if (step == null || step.isBlank()) {
      incrementDiscard("funnel_invalid");
      return;
    }
    String normalized = sanitizeFunnelStep(step);
    if (normalized == null) {
      incrementDiscard("funnel_invalid");
      return;
    }
    increment("funnel:" + normalized);
    recordObservabilityAction(normalized);
  }

  /** Records a refresh attempt for the admin metrics dashboard. */
  public void recordRefresh(boolean ok, long durationMs) {
    increment("refresh.count");
    if (ok) {
      increment("refresh.ok");
    } else {
      increment("refresh.error");
    }
    counters.put("refresh.last_duration_ms", durationMs);
    markDirty(System.currentTimeMillis());
  }

  /** Record an HTTP 5xx response for the given route. */
  public void recordServerError(String route) {
    increment("5xx:" + route);
  }

  /** Record the outcome of a WebSocket handshake. */
  public void recordWsHandshake(boolean ok) {
    increment(ok ? "ws.handshake.ok" : "ws.handshake.fail");
  }

  private void increment(String key) {
    long now = System.currentTimeMillis();
    int size = bufferSize.incrementAndGet();
    if (size > bufferMaxSize) {
      bufferSize.decrementAndGet();
      incrementDiscard("buffer_full");
      logBufferFullDiscard(now);
      updateHealthState();
      return;
    }
    if (!bufferWarned && size >= bufferMaxSize * 0.7) {
      LOG.warn("buffer_threshold_reached");
      bufferWarned = true;
    }
    counters.merge(key, 1L, Long::sum);
    markDirty(now);
    updateHealthState();
  }

  private void decrement(String key) {
    long now = System.currentTimeMillis();
    int size = bufferSize.incrementAndGet();
    if (size > bufferMaxSize) {
      bufferSize.decrementAndGet();
      incrementDiscard("buffer_full");
      logBufferFullDiscard(now);
      updateHealthState();
      return;
    }
    if (!bufferWarned && size >= bufferMaxSize * 0.7) {
      LOG.warn("buffer_threshold_reached");
      bufferWarned = true;
    }
    Long newVal = counters.merge(key, -1L, Long::sum);
    if (newVal != null && newVal <= 0L) {
      counters.remove(key, newVal);
    }
    markDirty(now);
    updateHealthState();
  }

  private void markDirty(long now) {
    if (dirty.compareAndSet(false, true)) {
      dirtySinceTime.set(now);
      return;
    }
    if (dirtySinceTime.get() == 0L) {
      dirtySinceTime.compareAndSet(0L, now);
    }
  }

  private void logBufferFullDiscard(long now) {
    long cooldownMs = Math.max(0L, bufferFullLogCooldown.toMillis());
    long last = lastBufferFullWarnAt.get();
    if (cooldownMs > 0 && now - last < cooldownMs) {
      return;
    }
    if (lastBufferFullWarnAt.compareAndSet(last, now)) {
      LOG.warn("discarded_buffer_full");
    }
  }

  private void incrementDiscard(String reason) {
    discardedByReason.computeIfAbsent(reason, r -> new LongAdder()).increment();
  }

  private static String sanitizeFunnelStep(String step) {
    String normalized = step.trim().toLowerCase();
    if (normalized.isEmpty()) {
      return null;
    }
    StringBuilder safe = new StringBuilder(normalized.length());
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if ((c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '.'
          || c == '-'
          || c == '_') {
        safe.append(c);
      }
    }
    if (safe.length() == 0 || safe.length() > 80) {
      return null;
    }
    return safe.toString();
  }

  private void recordObservabilityRoute(String route) {
    recordObservabilityModule(BusinessObservabilityTaxonomy.moduleForRoute(route));
  }

  private void recordObservabilityModule(String module) {
    recordObservabilitySeries(moduleHourly, moduleLastSeen, sanitizeObservabilityCode(module));
  }

  private void recordObservabilityAction(String rawAction) {
    String canonical = BusinessObservabilityTaxonomy.canonicalAction(rawAction);
    if (canonical == null) {
      return;
    }
    recordObservabilitySeries(actionHourly, actionLastSeen, sanitizeObservabilityCode(canonical));
  }

  private void recordObservabilitySeries(
      Map<String, Long> buckets, Map<String, Long> lastSeenMap, String code) {
    if (code == null) {
      return;
    }
    long now = System.currentTimeMillis();
    long epochHour = currentEpochHour(Instant.ofEpochMilli(now));
    buckets.merge(bucketKey(code, epochHour), 1L, Long::sum);
    lastSeenMap.put(code, now);
    pruneObservabilityBuckets(epochHour);
  }

  private void restoreObservability(Object raw) {
    if (!(raw instanceof Map<?, ?> observability)) {
      return;
    }
    restoreLongMap(observability.get("moduleHourly"), moduleHourly);
    restoreLongMap(observability.get("actionHourly"), actionHourly);
    restoreLongMap(observability.get("moduleLastSeen"), moduleLastSeen);
    restoreLongMap(observability.get("actionLastSeen"), actionLastSeen);
    pruneObservabilityBuckets(currentEpochHour(Instant.now()));
  }

  private void importLegacyObservabilityLedger() {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> raw = mapper.readValue(businessObservabilityV1Path.toFile(), Map.class);
      restoreLongMap(raw.get("moduleHourly"), moduleHourly);
      restoreLongMap(raw.get("actionHourly"), actionHourly);
      restoreLongMap(raw.get("moduleLastSeen"), moduleLastSeen);
      restoreLongMap(raw.get("actionLastSeen"), actionLastSeen);
      pruneObservabilityBuckets(currentEpochHour(Instant.now()));
      if (!moduleHourly.isEmpty() || !actionHourly.isEmpty()) {
        migrateObservabilityV1 = true;
      }
    } catch (Exception e) {
      LOG.warn("Failed to import legacy business observability ledger", e);
    }
  }

  private void restoreLongMap(Object raw, Map<String, Long> target) {
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

  private List<ObservabilitySeriesSnapshot> buildObservabilitySeries(
      List<String> codes,
      Map<String, Long> buckets,
      Map<String, Long> lastSeenMap,
      long firstEpochHour,
      long latestEpochHour,
      long previousFirstEpochHour) {
    List<ObservabilitySeriesSnapshot> rows = new ArrayList<>();
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
          new ObservabilitySeriesSnapshot(
              code,
              List.copyOf(counts),
              total,
              previousTotal,
              trendPct(total, previousTotal),
              lastSeenMap.get(code)));
    }
    rows.sort(
        java.util.Comparator.comparingLong(ObservabilitySeriesSnapshot::total)
            .reversed()
            .thenComparing(ObservabilitySeriesSnapshot::code));
    return List.copyOf(rows);
  }

  private void pruneObservabilityBuckets(long latestEpochHour) {
    long retention = Math.max(24, observabilityRetentionHours);
    long minEpochHour = latestEpochHour - retention;
    pruneObservabilityMap(moduleHourly, minEpochHour);
    pruneObservabilityMap(actionHourly, minEpochHour);
  }

  private void pruneObservabilityMap(Map<String, Long> buckets, long minEpochHour) {
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

  private Long trendPct(long current, long previous) {
    if (previous <= 0L) {
      return current > 0L ? 100L : null;
    }
    return Math.round(((double) (current - previous) / (double) previous) * 100d);
  }

  private String sanitizeObservabilityCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    String normalized = code.trim().toLowerCase();
    StringBuilder safe = new StringBuilder(normalized.length());
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if ((c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '.'
          || c == '-'
          || c == '_') {
        safe.append(c);
      }
    }
    if (safe.isEmpty() || safe.length() > 80) {
      return null;
    }
    return safe.toString();
  }

  private boolean isBurst(String sessionId) {
    if (sessionId == null)
      return false;
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
    if (!dirty.get())
      return;
    LOG.info("metrics_flush_start");
    Map<String, Long> snapshot = Map.copyOf(counters);
    Map<String, Long> discards = getDiscarded();
    Map<String, Object> file = new HashMap<>();
    file.put("counters", snapshot);
    Map<String, List<Registrant>> regs = new HashMap<>();
    registrations.forEach((k, v) -> regs.put(k, new ArrayList<>(v)));
    file.put("registrants", regs);
    Map<String, Object> observability = new HashMap<>();
    observability.put("moduleHourly", Map.copyOf(moduleHourly));
    observability.put("actionHourly", Map.copyOf(actionHourly));
    observability.put("moduleLastSeen", Map.copyOf(moduleLastSeen));
    observability.put("actionLastSeen", Map.copyOf(actionLastSeen));
    file.put("observability", observability);
    Map<String, Object> meta = new HashMap<>();
    meta.put("schemaVersion", CURRENT_SCHEMA_VERSION);
    meta.put("lastFlush", System.currentTimeMillis());
    meta.put("discarded", discards);
    file.put("meta", meta);
    Path tmp = metricsV2Path.resolveSibling("metrics-v2.json.tmp");
    attemptFlush(file, meta, tmp, 1, 50L);
  }

  private void attemptFlush(
      Map<String, Object> file, Map<String, Object> meta, Path tmp, int attempt, long backoff) {
    try {
      Files.createDirectories(metricsV2Path.getParent());
      byte[] json = mapper.writeValueAsBytes(file);
      meta.put("fileSizeBytes", json.length);
      json = mapper.writeValueAsBytes(file);
      Files.write(tmp, json);
      Files.move(
          tmp, metricsV2Path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      metricsPath = metricsV2Path;
      schemaVersion = CURRENT_SCHEMA_VERSION;
      lastFileSizeBytes = json.length;
      lastFlushTime = System.currentTimeMillis();
      bufferSize.set(0);
      bufferWarned = false;
      dirtySinceTime.set(0L);
      writesOk.incrementAndGet();
      lastError = null;
      if (migrateFromV1) {
        try {
          Files.deleteIfExists(metricsV1Path);
        } catch (IOException ignored) {
        }
        migrateFromV1 = false;
      }
      if (migrateObservabilityV1) {
        try {
          Files.deleteIfExists(businessObservabilityV1Path);
        } catch (IOException ignored) {
        }
        migrateObservabilityV1 = false;
      }
      dirty.set(false);
      LOG.info("metrics_flush_ok");
      updateHealthState();
    } catch (Exception e) {
      lastError = e.getMessage();
      if (attempt >= 3) {
        LOG.error("metrics_flush_fail");
        increment("discarded_events");
        flushFailures.incrementAndGet();
        incrementDiscard("invalid");
        writesFail.incrementAndGet();
        updateHealthState();
      } else {
        long nextBackoff = backoff * 2;
        scheduler.schedule(
            () -> attemptFlush(file, meta, tmp, attempt + 1, nextBackoff),
            backoff,
            TimeUnit.MILLISECONDS);
      }
    }
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
    snap.put("buffer_full", snap.getOrDefault("buffer_full", 0L));
    return snap;
  }

  public record Config(
      long talkViewWindowSeconds,
      long eventViewWindowSeconds,
      long stageVisitWindowSeconds,
      boolean pageViewDedupe,
      int burstPerSecond,
      int burstPerMinute) {
    public long stageVisitWindowMinutes() {
      return stageVisitWindowSeconds / 60;
    }
  }

  public Config getConfig() {
    return new Config(
        talkViewWindow.getSeconds(),
        eventViewWindow.getSeconds(),
        stageVisitWindow.getSeconds(),
        pageViewDedupe,
        burstPerSecond,
        burstPerMinute);
  }

  /**
   * Returns the last modification time of the metrics file or {@code 0} if
   * unavailable.
   */
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

  public record Health(
      HealthState estado,
      long lastFlush,
      long flushIntervalMillis,
      int bufferCurrentSize,
      int bufferMaxSize,
      long writesOk,
      long writesFail,
      String lastError,
      long fileSizeBytes,
      Map<String, Long> discards) {
  }

  public enum HealthState {
    OK,
    DEGRADADO,
    ERROR
  }

  public Health getHealth() {
    return new Health(
        currentState,
        lastFlushTime,
        flushInterval.toMillis(),
        bufferSize.get(),
        bufferMaxSize,
        writesOk.get(),
        writesFail.get(),
        lastError,
        lastFileSizeBytes,
        getDiscarded());
  }

  private HealthState computeState() {
    long now = System.currentTimeMillis();
    long interval = flushInterval.toMillis();
    int buf = bufferSize.get();
    long fail = writesFail.get();
    if (buf >= bufferMaxSize || fail >= 3) {
      return HealthState.ERROR;
    }
    if (buf >= (int) (bufferMaxSize * 0.7) || fail >= 1) {
      return HealthState.DEGRADADO;
    }
    if (dirty.get()) {
      long since = dirtySinceTime.get();
      if (since <= 0L) {
        since = lastFlushTime;
      }
      long pendingAge = now - since;
      if (pendingAge >= interval * 5) {
        return HealthState.ERROR;
      }
      if (pendingAge >= interval * 2) {
        return HealthState.DEGRADADO;
      }
    }
    return HealthState.OK;
  }

  private void updateHealthState() {
    HealthState newState = computeState();
    if (newState != currentState) {
      long now = System.currentTimeMillis();
      long cooldownMs = Math.max(0L, healthLogCooldown.toMillis());
      if (cooldownMs <= 0L || now - lastHealthTransitionLogTime >= cooldownMs) {
        LOG.warnf("health_state_change %s->%s", currentState, newState);
        lastHealthTransitionLogTime = now;
      }
      currentState = newState;
    }
  }

  /**
   * Resets all tracked metrics. Intended for administrative use and tests. Clears
   * in-memory
   * counters and removes any persisted metrics files so that a fresh dataset can
   * be recorded.
   */
  public void reset() {
    counters.clear();
    talkViews.clear();
    eventViews.clear();
    stageVisits.clear();
    pageViews.clear();
    moduleHourly.clear();
    actionHourly.clear();
    moduleLastSeen.clear();
    actionLastSeen.clear();
    rates.clear();
    discardedByReason.clear();
    registrations.clear();
    bufferSize.set(0);
    bufferWarned = false;
    dirty.set(false);
    dirtySinceTime.set(0L);
    writesOk.set(0);
    writesFail.set(0);
    flushFailures.set(0);
    lastFlushTime = System.currentTimeMillis();
    lastHealthTransitionLogTime = 0L;
    lastBufferFullWarnAt.set(0L);
    lastError = null;
    currentState = HealthState.OK;
    lastFileSizeBytes = 0;
    schemaVersion = CURRENT_SCHEMA_VERSION;
    metricsPath = metricsV2Path;
    try {
      Files.deleteIfExists(metricsV1Path);
      Files.deleteIfExists(metricsV2Path);
      Files.deleteIfExists(businessObservabilityV1Path);
    } catch (IOException e) {
      LOG.warn("Failed to delete metrics file", e);
    }
  }

  private static class RateLimiter {
    long secondStart = System.currentTimeMillis();
    int secondCount = 0;
    long minuteStart = System.currentTimeMillis();
    int minuteCount = 0;
  }
}
