package com.scanales.eventflow.insights;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Append-only development insights ledger stored in filesystem to keep ADEV operations auditable
 * without adding external infrastructure.
 */
@ApplicationScoped
public class DevelopmentInsightsLedgerService {

  private static final Logger LOG = Logger.getLogger(DevelopmentInsightsLedgerService.class);
  private static final int TRIM_BATCH = 256;

  @Inject ObjectMapper objectMapper;

  @ConfigProperty(name = "insights.ledger.enabled", defaultValue = "true")
  boolean enabled;

  @ConfigProperty(name = "insights.ledger.max-entries", defaultValue = "20000")
  int maxEntries;

  @ConfigProperty(name = "insights.ledger.stale-minutes", defaultValue = "1440")
  long staleMinutesThreshold;

  @ConfigProperty(name = "homedir.data.dir", defaultValue = "data")
  String dataDirPath;

  @ConfigProperty(name = "insights.ledger.file", defaultValue = "insights/initiative-ledger.ndjson")
  String ledgerRelativePath;

  private final ReentrantLock lock = new ReentrantLock();
  private final Deque<DevelopmentInsightsEvent> events = new ArrayDeque<>();
  private final Map<String, MutableInitiativeSummary> initiatives = new LinkedHashMap<>();

  private Path ledgerPath;
  private ObjectMapper mapper;
  private volatile Instant lastEventAt;
  private volatile long compactions;
  private volatile long loadErrors;
  private volatile long writeErrors;

  @PostConstruct
  void init() {
    mapper = objectMapper.copy();
    if (maxEntries < 100) {
      maxEntries = 100;
    }
    if (staleMinutesThreshold < 1) {
      staleMinutesThreshold = 1L;
    }
    String sysProp = System.getProperty("homedir.data.dir");
    if (sysProp != null && !sysProp.isBlank()) {
      dataDirPath = sysProp;
    }
    Path baseDir = Paths.get(dataDirPath);
    ledgerPath = baseDir.resolve(ledgerRelativePath).normalize();
    if (!enabled) {
      LOG.info("insights_ledger_disabled");
      return;
    }
    try {
      Path parent = ledgerPath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      loadLedger();
      LOG.infov(
          "insights_ledger_ready path={0} events={1} initiatives={2}",
          ledgerPath.toAbsolutePath(),
          events.size(),
          initiatives.size());
    } catch (IOException e) {
      LOG.error("insights_ledger_init_error", e);
      loadErrors++;
    }
  }

  public DevelopmentInsightsStatus status() {
    lock.lock();
    try {
      int startedCount = 0;
      int mergedCount = 0;
      int prodVerifiedCount = 0;
      int prValidationPassedCount = 0;
      int prValidationFailedCount = 0;
      int productionReleaseFailedCount = 0;
      int eventsLast7DaysCount = 0;
      int eventsPrevious7DaysCount = 0;
      Map<String, Integer> eventTypeCountsLast7Days = new LinkedHashMap<>();
      Set<String> activeInitiativesLast7Days = new HashSet<>();
      long sumLeadMerge = 0L;
      long sumLeadProd = 0L;
      int countLeadMerge = 0;
      int countLeadProd = 0;
      Instant now = Instant.now();
      Instant sevenDaysAgo = now.minus(Duration.ofDays(7));
      Instant fourteenDaysAgo = now.minus(Duration.ofDays(14));
      for (DevelopmentInsightsEvent event : events) {
        if (event == null || event.type() == null) {
          continue;
        }
        Instant eventAt = event.at();
        if (eventAt != null) {
          if (!eventAt.isBefore(sevenDaysAgo)) {
            eventsLast7DaysCount++;
            activeInitiativesLast7Days.add(event.initiativeId());
            eventTypeCountsLast7Days.merge(event.type(), 1, Integer::sum);
          } else if (!eventAt.isBefore(fourteenDaysAgo)) {
            eventsPrevious7DaysCount++;
          }
        }
        if ("PR_VALIDATION_PASSED".equals(event.type())) {
          prValidationPassedCount++;
        } else if ("PR_VALIDATION_FAILED".equals(event.type())) {
          prValidationFailedCount++;
        } else if ("PRODUCTION_RELEASE_FAILED".equals(event.type())) {
          productionReleaseFailedCount++;
        }
      }
      for (MutableInitiativeSummary summary : initiatives.values()) {
        String state = summary.state;
        if ("prod_verified".equals(state)) {
          prodVerifiedCount++;
          mergedCount++;
          startedCount++;
        } else if ("merged".equals(state)) {
          mergedCount++;
          startedCount++;
        } else if ("started".equals(state)) {
          startedCount++;
        }
        InitiativeSummary snapshot = summary.snapshot();
        if (snapshot.leadHoursToMerge() != null) {
          sumLeadMerge += snapshot.leadHoursToMerge();
          countLeadMerge++;
        }
        if (snapshot.leadHoursToProduction() != null) {
          sumLeadProd += snapshot.leadHoursToProduction();
          countLeadProd++;
        }
      }
      int prValidationTotalCount = prValidationPassedCount + prValidationFailedCount;
      int productionOutcomeTotalCount = prodVerifiedCount + productionReleaseFailedCount;
      Long minutesSinceLast = minutesSince(lastEventAt, now);
      boolean staleState = isStale(minutesSinceLast, staleMinutesThreshold);
      return new DevelopmentInsightsStatus(
          enabled,
          ledgerPath != null ? ledgerPath.toString() : "",
          safeFileSize(ledgerPath),
          maxEntries,
          events.size(),
          initiatives.size(),
          startedCount,
          mergedCount,
          prodVerifiedCount,
          prValidationPassedCount,
          prValidationFailedCount,
          productionReleaseFailedCount,
          prValidationTotalCount,
          percentage(prValidationPassedCount, prValidationTotalCount),
          productionOutcomeTotalCount,
          percentage(prodVerifiedCount, productionOutcomeTotalCount),
          averageHours(sumLeadMerge, countLeadMerge),
          averageHours(sumLeadProd, countLeadProd),
          eventsLast7DaysCount,
          eventsPrevious7DaysCount,
          percentageDelta(eventsLast7DaysCount, eventsPrevious7DaysCount),
          activeInitiativesLast7Days.size(),
          topCounts(eventTypeCountsLast7Days, 5),
          minutesSinceLast,
          staleState,
          lastEventAt,
          compactions,
          loadErrors,
          writeErrors);
    } finally {
      lock.unlock();
    }
  }

  public List<InitiativeSummary> listInitiatives(int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    int safeOffset = Math.max(0, offset);
    lock.lock();
    try {
      return initiatives.values().stream()
          .map(MutableInitiativeSummary::snapshot)
          .sorted(Comparator.comparing(InitiativeSummary::lastEventAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
          .skip(safeOffset)
          .limit(safeLimit)
          .toList();
    } finally {
      lock.unlock();
    }
  }

  public DevelopmentInsightsEvent startInitiative(
      String initiativeId, String title, String definitionStartedAt, Map<String, String> extraMetadata) {
    Map<String, String> metadata = new LinkedHashMap<>(sanitizeMetadata(extraMetadata));
    if (title != null && !title.isBlank()) {
      metadata.put("title", trimValue(title, 240));
    }
    if (definitionStartedAt != null && !definitionStartedAt.isBlank()) {
      metadata.put("definition_started_at", trimValue(definitionStartedAt, 64));
    }
    return append(initiativeId, "INITIATIVE_STARTED", metadata);
  }

  public DevelopmentInsightsEvent append(
      String initiativeId, String type, Map<String, String> metadata) {
    ensureEnabled();
    String safeInitiativeId = sanitizeInitiativeId(initiativeId);
    String safeType = sanitizeType(type);
    Map<String, String> safeMetadata = sanitizeMetadata(metadata);
    DevelopmentInsightsEvent event =
        new DevelopmentInsightsEvent(
            UUID.randomUUID().toString(), safeInitiativeId, safeType, Instant.now(), safeMetadata);
    lock.lock();
    try {
      appendLine(event);
      applyEventLocked(event);
      maybeTrimAndCompactLocked();
      return event;
    } finally {
      lock.unlock();
    }
  }

  private void ensureEnabled() {
    if (!enabled) {
      throw new IllegalStateException("insights_ledger_disabled");
    }
  }

  private void loadLedger() {
    lock.lock();
    try {
      events.clear();
      initiatives.clear();
      lastEventAt = null;
      if (ledgerPath == null || !Files.exists(ledgerPath)) {
        return;
      }
      try (BufferedReader reader = Files.newBufferedReader(ledgerPath, StandardCharsets.UTF_8)) {
        String line;
        while ((line = reader.readLine()) != null) {
          String raw = line.trim();
          if (raw.isEmpty()) {
            continue;
          }
          try {
            DevelopmentInsightsEvent event = mapper.readValue(raw, DevelopmentInsightsEvent.class);
            if (event == null || event.initiativeId() == null || event.type() == null || event.at() == null) {
              loadErrors++;
              continue;
            }
            applyEventLocked(normalizeEvent(event));
          } catch (Exception e) {
            loadErrors++;
          }
        }
      }
      maybeTrimAndCompactLocked();
    } catch (IOException e) {
      loadErrors++;
      LOG.warn("insights_ledger_load_failed", e);
    } finally {
      lock.unlock();
    }
  }

  private void appendLine(DevelopmentInsightsEvent event) {
    if (ledgerPath == null) {
      throw new IllegalStateException("insights_ledger_not_initialized");
    }
    try {
      Path parent = ledgerPath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      String json = mapper.writeValueAsString(event);
      try (BufferedWriter writer =
          Files.newBufferedWriter(
              ledgerPath,
              StandardCharsets.UTF_8,
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE,
              StandardOpenOption.APPEND)) {
        writer.write(json);
        writer.newLine();
      }
    } catch (IOException e) {
      writeErrors++;
      throw new IllegalStateException("insights_ledger_write_failed", e);
    }
  }

  private void applyEventLocked(DevelopmentInsightsEvent event) {
    DevelopmentInsightsEvent safe = normalizeEvent(event);
    events.addLast(safe);
    lastEventAt = safe.at();
    MutableInitiativeSummary summary =
        initiatives.computeIfAbsent(safe.initiativeId(), MutableInitiativeSummary::new);
    summary.onEvent(safe);
  }

  private void maybeTrimAndCompactLocked() {
    int threshold = maxEntries + TRIM_BATCH;
    if (events.size() <= threshold) {
      return;
    }
    while (events.size() > maxEntries) {
      events.removeFirst();
    }
    rebuildInitiativesLocked();
    compactLedgerLocked();
  }

  private void rebuildInitiativesLocked() {
    initiatives.clear();
    for (DevelopmentInsightsEvent event : events) {
      MutableInitiativeSummary summary =
          initiatives.computeIfAbsent(event.initiativeId(), MutableInitiativeSummary::new);
      summary.onEvent(event);
    }
  }

  private void compactLedgerLocked() {
    if (ledgerPath == null) {
      return;
    }
    Path tmp = ledgerPath.resolveSibling(ledgerPath.getFileName().toString() + ".tmp");
    try (BufferedWriter writer =
        Files.newBufferedWriter(
            tmp,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {
      for (DevelopmentInsightsEvent event : events) {
        writer.write(mapper.writeValueAsString(event));
        writer.newLine();
      }
      writer.flush();
      Files.move(tmp, ledgerPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      compactions++;
    } catch (Exception e) {
      writeErrors++;
      LOG.warn("insights_ledger_compaction_failed", e);
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException ignored) {
      }
    }
  }

  private DevelopmentInsightsEvent normalizeEvent(DevelopmentInsightsEvent event) {
    String safeInitiative = sanitizeInitiativeId(event.initiativeId());
    String safeType = sanitizeType(event.type());
    Map<String, String> safeMetadata = sanitizeMetadata(event.metadata());
    Instant safeAt = Objects.requireNonNullElse(event.at(), Instant.now());
    String safeEventId =
        event.eventId() != null && !event.eventId().isBlank() ? event.eventId() : UUID.randomUUID().toString();
    return new DevelopmentInsightsEvent(safeEventId, safeInitiative, safeType, safeAt, safeMetadata);
  }

  private String sanitizeInitiativeId(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("initiative_id_required");
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    StringBuilder safe = new StringBuilder(normalized.length());
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if ((c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_'
          || c == '.') {
        safe.append(c);
      }
    }
    if (safe.isEmpty() || safe.length() > 120) {
      throw new IllegalArgumentException("initiative_id_invalid");
    }
    return safe.toString();
  }

  private String sanitizeType(String rawType) {
    if (rawType == null || rawType.isBlank()) {
      throw new IllegalArgumentException("type_required");
    }
    String normalized = rawType.trim().toUpperCase(Locale.ROOT);
    StringBuilder safe = new StringBuilder(normalized.length());
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
        safe.append(c);
      }
    }
    if (safe.isEmpty() || safe.length() > 64) {
      throw new IllegalArgumentException("type_invalid");
    }
    return safe.toString();
  }

  private Map<String, String> sanitizeMetadata(Map<String, String> raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    LinkedHashMap<String, String> safe = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : raw.entrySet()) {
      if (entry == null || entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      if (safe.size() >= 64) {
        break;
      }
      String key = sanitizeMetadataKey(entry.getKey());
      if (key == null) {
        continue;
      }
      safe.put(key, trimValue(entry.getValue(), 512));
    }
    return safe.isEmpty() ? Map.of() : Map.copyOf(safe);
  }

  private String sanitizeMetadataKey(String key) {
    String normalized = key.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      return null;
    }
    StringBuilder safe = new StringBuilder(normalized.length());
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if ((c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_'
          || c == '.') {
        safe.append(c);
      }
    }
    if (safe.isEmpty() || safe.length() > 80) {
      return null;
    }
    return safe.toString();
  }

  private String trimValue(String value, int maxLen) {
    String normalized = value.trim();
    if (normalized.length() <= maxLen) {
      return normalized;
    }
    return normalized.substring(0, maxLen);
  }

  private long safeFileSize(Path path) {
    if (path == null) {
      return 0L;
    }
    try {
      return Files.exists(path) ? Files.size(path) : 0L;
    } catch (IOException e) {
      return 0L;
    }
  }

  private static Instant parseInstantSafe(String value, Instant fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Instant.parse(value.trim());
    } catch (DateTimeParseException ignored) {
      return fallback;
    }
  }

  private static final class MutableInitiativeSummary {
    private final String initiativeId;
    private String title;
    private String state = "active";
    private Instant definitionStartedAt;
    private Instant prOpenedAt;
    private Instant prMergedAt;
    private Instant productionVerifiedAt;
    private Instant lastEventAt;
    private String lastEventType;
    private long totalEvents;

    private MutableInitiativeSummary(String initiativeId) {
      this.initiativeId = initiativeId;
    }

    private void onEvent(DevelopmentInsightsEvent event) {
      totalEvents++;
      lastEventAt = event.at();
      lastEventType = event.type();
      Map<String, String> metadata = event.metadata();
      if (metadata != null) {
        String incomingTitle = metadata.get("title");
        if (incomingTitle != null && !incomingTitle.isBlank()) {
          title = incomingTitle;
        }
        String explicitState = metadata.get("state");
        if (explicitState != null && !explicitState.isBlank()) {
          state = explicitState;
        }
      }
      if ("INITIATIVE_STARTED".equals(event.type())) {
        Instant definitionStart = parseInstantSafe(metadata != null ? metadata.get("definition_started_at") : null, event.at());
        if (definitionStartedAt == null || definitionStart.isBefore(definitionStartedAt)) {
          definitionStartedAt = definitionStart;
        }
        state = "started";
      } else if ("PR_OPENED".equals(event.type())) {
        if (prOpenedAt == null || event.at().isBefore(prOpenedAt)) {
          prOpenedAt = event.at();
        }
      } else if ("PRODUCTION_VERIFIED".equals(event.type())) {
        if (productionVerifiedAt == null || event.at().isBefore(productionVerifiedAt)) {
          productionVerifiedAt = event.at();
        }
        state = "prod_verified";
      } else if ("PR_MERGED".equals(event.type()) && !"prod_verified".equals(state)) {
        if (prMergedAt == null || event.at().isBefore(prMergedAt)) {
          prMergedAt = event.at();
        }
        state = "merged";
      }
      if (definitionStartedAt == null) {
        definitionStartedAt = event.at();
      }
    }

    private InitiativeSummary snapshot() {
      Long leadToMerge = safeLeadHours(definitionStartedAt, prMergedAt);
      Long leadToProd = safeLeadHours(definitionStartedAt, productionVerifiedAt);
      return new InitiativeSummary(
          initiativeId,
          title != null ? title : initiativeId,
          state,
          definitionStartedAt,
          definitionStartedAt,
          prOpenedAt,
          prMergedAt,
          productionVerifiedAt,
          leadToMerge,
          leadToProd,
          lastEventAt,
          lastEventType,
          totalEvents);
    }
  }

  private static Long safeLeadHours(Instant from, Instant to) {
    if (from == null || to == null) {
      return null;
    }
    long hours = Duration.between(from, to).toHours();
    return Math.max(hours, 0L);
  }

  private static Long averageHours(long sum, int count) {
    if (count <= 0) {
      return null;
    }
    return Math.round((double) sum / (double) count);
  }

  private static Long percentage(int numerator, int denominator) {
    if (denominator <= 0) {
      return null;
    }
    return Math.round(((double) numerator * 100d) / (double) denominator);
  }

  private static Long percentageDelta(int current, int previous) {
    if (previous <= 0) {
      return null;
    }
    return Math.round((((double) current - (double) previous) * 100d) / (double) previous);
  }

  private static Long minutesSince(Instant at, Instant now) {
    if (at == null || now == null) {
      return null;
    }
    long minutes = Duration.between(at, now).toMinutes();
    return Math.max(minutes, 0L);
  }

  private static boolean isStale(Long minutesSinceLastEvent, long thresholdMinutes) {
    if (minutesSinceLastEvent == null) {
      return true;
    }
    return minutesSinceLastEvent > thresholdMinutes;
  }

  private static Map<String, Integer> topCounts(Map<String, Integer> counts, int limit) {
    if (counts == null || counts.isEmpty() || limit <= 0) {
      return Map.of();
    }
    return counts.entrySet().stream()
        .sorted(
            Comparator.comparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey))
        .limit(limit)
        .collect(
            LinkedHashMap::new,
            (m, e) -> m.put(e.getKey(), e.getValue()),
            LinkedHashMap::putAll);
  }
}
