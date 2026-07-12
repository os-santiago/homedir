package com.scanales.homedir.sdlc;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jboss.logging.Logger;

/** Builds dashboard telemetry away from HTTP request threads. */
@ApplicationScoped
public class SdlcDashboardSnapshot {
  private static final Logger LOG = Logger.getLogger(SdlcDashboardSnapshot.class);
  private final SdlcObservabilityService source;
  private final AtomicReference<Map<String, Object>> current =
      new AtomicReference<>(empty("Telemetry snapshot is starting"));

  public SdlcDashboardSnapshot(SdlcObservabilityService source) {
    this.source = source;
  }

  @PostConstruct
  void initialize() {
    refresh();
  }

  @Scheduled(
      every = "${sdlc.dashboard.snapshot-interval:30s}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void refresh() {
    try {
      current.set(
          Map.of(
              "status", source.status(),
              "pipeline", source.pipeline(),
              "issues", source.issues(),
              "prs", source.prs(),
              "metrics", source.metrics(30),
              "metricsByRange",
                  Map.of("7", source.metrics(7), "30", source.metrics(30), "90", source.metrics(90)),
              "anomalies", source.anomalies(),
              "configuration", source.configuration(),
              "generatedAt", Instant.now().toString(),
              "stale", false));
    } catch (RuntimeException e) {
      LOG.warn("SDLC snapshot refresh failed; preserving the previous snapshot", e);
      if (Boolean.TRUE.equals(current.get().get("initializing"))) {
        current.set(empty("Telemetry is temporarily unavailable"));
      }
    }
  }

  public Map<String, Object> get() {
    return current.get();
  }

  private static Map<String, Object> empty(String detail) {
    return Map.ofEntries(
        Map.entry(
            "status",
            Map.of(
                "worker", Map.of("state", "unknown", "heartbeatAge", 0, "detail", detail),
                "components", Map.of(),
                "generatedAt", Instant.now().toString())),
        Map.entry("pipeline", List.of()),
        Map.entry("issues", List.of()),
        Map.entry("prs", List.of()),
        Map.entry("metrics", Map.of()),
        Map.entry("metricsByRange", Map.of()),
        Map.entry("anomalies", List.of()),
        Map.entry(
            "configuration",
            Map.of(
                "workerVersion", "unknown",
                "stateDirectory", "unavailable",
                "labels", List.of(),
                "timeouts", Map.of(),
                "paused", false,
                "controlsEnabled", false)),
        Map.entry("generatedAt", Instant.now().toString()),
        Map.entry("stale", true),
        Map.entry("initializing", true));
  }
}
