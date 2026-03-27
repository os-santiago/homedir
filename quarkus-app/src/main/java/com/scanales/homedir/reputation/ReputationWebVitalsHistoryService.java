package com.scanales.homedir.reputation;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ReputationWebVitalsHistoryService {

  @ConfigProperty(name = "reputation.webvitals.history.max-snapshots", defaultValue = "120")
  int maxSnapshots;

  private final List<Snapshot> snapshots = new ArrayList<>();

  public synchronized TrendWindow recordAndTrend(Map<String, RouteTotals> routes) {
    Snapshot current = new Snapshot(System.currentTimeMillis(), deepCopy(routes));
    Snapshot last = snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1);
    if (last == null || !sameTotals(last.routes(), current.routes())) {
      snapshots.add(current);
      trimToMax();
      last = current;
    }

    Snapshot previous = snapshots.size() > 1 ? snapshots.get(snapshots.size() - 2) : null;
    if (previous == null) {
      Map<String, RouteTrend> insufficient =
          Map.of("hub", RouteTrend.insufficient(), "how", RouteTrend.insufficient());
      return new TrendWindow(snapshots.size(), insufficient);
    }

    Map<String, RouteTrend> trends =
        Map.of(
            "hub", computeTrend(previous.routes().get("hub"), last.routes().get("hub")),
            "how", computeTrend(previous.routes().get("how"), last.routes().get("how")));
    return new TrendWindow(snapshots.size(), trends);
  }

  private void trimToMax() {
    int safeMax = Math.max(10, maxSnapshots);
    while (snapshots.size() > safeMax) {
      snapshots.remove(0);
    }
  }

  private boolean sameTotals(Map<String, RouteTotals> first, Map<String, RouteTotals> second) {
    if (first == null || second == null) {
      return false;
    }
    RouteTotals firstHub = first.get("hub");
    RouteTotals secondHub = second.get("hub");
    RouteTotals firstHow = first.get("how");
    RouteTotals secondHow = second.get("how");
    return safeEquals(firstHub, secondHub) && safeEquals(firstHow, secondHow);
  }

  private boolean safeEquals(RouteTotals first, RouteTotals second) {
    if (first == null || second == null) {
      return false;
    }
    return first.samples() == second.samples()
        && first.lcp().equals(second.lcp())
        && first.inp().equals(second.inp());
  }

  private Map<String, RouteTotals> deepCopy(Map<String, RouteTotals> routes) {
    Map<String, RouteTotals> copy = new ConcurrentHashMap<>();
    for (Map.Entry<String, RouteTotals> entry : routes.entrySet()) {
      RouteTotals value = entry.getValue();
      if (value == null) {
        continue;
      }
      copy.put(
          entry.getKey(),
          new RouteTotals(value.samples(), Map.copyOf(value.lcp()), Map.copyOf(value.inp())));
    }
    return Map.copyOf(copy);
  }

  private RouteTrend computeTrend(RouteTotals previous, RouteTotals current) {
    if (previous == null || current == null) {
      return RouteTrend.insufficient();
    }
    long samplesDelta = Math.max(0L, current.samples() - previous.samples());
    if (samplesDelta <= 0) {
      return new RouteTrend(0L, 0L, 0L, 0L, 0L, "no_new_samples");
    }

    long lcpPoorDelta = delta(current.lcp(), previous.lcp(), "poor");
    long lcpNeedsDelta = delta(current.lcp(), previous.lcp(), "needs_improvement");
    long inpPoorDelta = delta(current.inp(), previous.inp(), "poor");
    long inpNeedsDelta = delta(current.inp(), previous.inp(), "needs_improvement");

    long badDelta = lcpPoorDelta + lcpNeedsDelta + inpPoorDelta + inpNeedsDelta;
    double badRate = (double) badDelta / (double) (samplesDelta * 2L);

    String status;
    if (badRate > 0.45d) {
      status = "worsening";
    } else if (badRate < 0.20d) {
      status = "improving";
    } else {
      status = "stable";
    }
    return new RouteTrend(samplesDelta, lcpPoorDelta, lcpNeedsDelta, inpPoorDelta, inpNeedsDelta, status);
  }

  private long delta(Map<String, Long> current, Map<String, Long> previous, String key) {
    long c = current.getOrDefault(key, 0L);
    long p = previous.getOrDefault(key, 0L);
    return Math.max(0L, c - p);
  }

  public record RouteTotals(long samples, Map<String, Long> lcp, Map<String, Long> inp) {}

  public record Snapshot(long capturedAtMillis, Map<String, RouteTotals> routes) {}

  public record TrendWindow(long windowSize, Map<String, RouteTrend> routes) {}

  public record RouteTrend(
      long samplesDelta,
      long lcpPoorDelta,
      long lcpNeedsDelta,
      long inpPoorDelta,
      long inpNeedsDelta,
      String status) {
    static RouteTrend insufficient() {
      return new RouteTrend(0L, 0L, 0L, 0L, 0L, "insufficient_data");
    }
  }
}
