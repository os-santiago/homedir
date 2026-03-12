package com.scanales.eventflow.observability;

import com.scanales.eventflow.insights.DevelopmentInsightsLedgerService;
import com.scanales.eventflow.insights.DevelopmentInsightsStatus;
import com.scanales.eventflow.service.UsageMetricsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Combines product usage signals and delivery insights into a business observability snapshot. */
@ApplicationScoped
public class BusinessObservabilityService {

  private static final DateTimeFormatter LAST_SEEN_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

  @Inject BusinessObservabilityLedgerService ledgerService;

  @Inject UsageMetricsService usageMetricsService;

  @Inject DevelopmentInsightsLedgerService insightsLedgerService;

  public DashboardSnapshot dashboard(int hours) {
    BusinessObservabilityLedgerService.ObservabilityWindow window = ledgerService.window(hours);
    DevelopmentInsightsStatus insights = insightsLedgerService.status();
    long activeModules = window.modules().stream().filter(row -> row.total() > 0L).count();
    HeatRow hottestModule =
        window.modules().stream().findFirst().map(this::toHeatRow).orElse(null);
    List<HeatRow> heatmap = window.modules().stream().map(this::toHeatRow).toList();
    List<ActivityRow> hotActions = window.actions().stream().limit(8).map(this::toActionRow).toList();
    List<FrictionRow> frictionWatch = buildFrictionWatch(window);
    DeliveryPulse deliveryPulse = buildDeliveryPulse(insights);
    return new DashboardSnapshot(
        window.generatedAtMillis(),
        window.windowHours(),
        window.hourLabels(),
        heatmap,
        hotActions,
        frictionWatch,
        deliveryPulse,
        window.interactionsLastWindow(),
        trendPct(window.interactionsLastWindow(), window.interactionsPreviousWindow()),
        activeModules,
        hottestModule,
        usageMetricsService.getSummary().discardedEvents());
  }

  public record DashboardSnapshot(
      long generatedAtMillis,
      int windowHours,
      List<String> hourLabels,
      List<HeatRow> heatmap,
      List<ActivityRow> hotActions,
      List<FrictionRow> frictionWatch,
      DeliveryPulse deliveryPulse,
      long interactionsLastWindow,
      Long interactionsTrendPct,
      long activeModules,
      HeatRow hottestModule,
      long discardedMetricsEvents) {}

  public record HeatRow(
      String code,
      long total,
      Long trendPct,
      String lastSeenLabel,
      String status,
      List<Long> counts) {}

  public record ActivityRow(
      String code, String module, long total, Long trendPct, String lastSeenLabel) {}

  public record FrictionRow(
      String module, long interactions, long keyActions, Long conversionPct, String severity) {}

  public record DeliveryPulse(
      long eventsLast24Hours,
      long activeInitiativesLast24Hours,
      Long productionSuccessRatePctLast7Days,
      Long prValidationSuccessRatePctLast7Days,
      boolean stale,
      Long minutesSinceLastEvent) {}

  private HeatRow toHeatRow(BusinessObservabilityLedgerService.SeriesSnapshot row) {
    String status = row.total() >= 12 ? "hot" : row.total() >= 4 ? "active" : "light";
    return new HeatRow(
        row.code(),
        row.total(),
        row.trendPct(),
        formatLastSeen(row.lastSeenAt()),
        status,
        row.counts());
  }

  private ActivityRow toActionRow(BusinessObservabilityLedgerService.SeriesSnapshot row) {
    return new ActivityRow(
        row.code(),
        BusinessObservabilityTaxonomy.moduleForAction(row.code()),
        row.total(),
        row.trendPct(),
        formatLastSeen(row.lastSeenAt()));
  }

  private List<FrictionRow> buildFrictionWatch(
      BusinessObservabilityLedgerService.ObservabilityWindow window) {
    Map<String, Long> moduleTotals =
        window.modules().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    BusinessObservabilityLedgerService.SeriesSnapshot::code,
                    BusinessObservabilityLedgerService.SeriesSnapshot::total));
    Map<String, Long> actionByModule = new java.util.LinkedHashMap<>();
    for (BusinessObservabilityLedgerService.SeriesSnapshot action : window.actions()) {
      actionByModule.merge(
          BusinessObservabilityTaxonomy.moduleForAction(action.code()), action.total(), Long::sum);
    }
    List<FrictionRow> rows = new ArrayList<>();
    for (String module : BusinessObservabilityTaxonomy.moduleOrder()) {
      long interactions = moduleTotals.getOrDefault(module, 0L);
      if (interactions < 5L) {
        continue;
      }
      long keyActions = actionByModule.getOrDefault(module, 0L);
      long conversionPct = Math.round((double) keyActions * 100d / interactions);
      if (conversionPct > 45L) {
        continue;
      }
      String severity = conversionPct <= 15L ? "high" : "watch";
      rows.add(new FrictionRow(module, interactions, keyActions, conversionPct, severity));
    }
    rows.sort(
        Comparator.comparing((FrictionRow row) -> row.severity().equals("high") ? 0 : 1)
            .thenComparing(FrictionRow::conversionPct)
            .thenComparing(FrictionRow::module));
    if (rows.size() > 5) {
      return List.copyOf(rows.subList(0, 5));
    }
    return List.copyOf(rows);
  }

  private DeliveryPulse buildDeliveryPulse(DevelopmentInsightsStatus insights) {
    return new DeliveryPulse(
        insights.eventsLast24Hours(),
        insights.activeInitiativesLast24Hours(),
        insights.productionSuccessRatePctLast7Days(),
        insights.prValidationSuccessRatePctLast7Days(),
        insights.stale(),
        insights.minutesSinceLastEvent());
  }

  private String formatLastSeen(Long epochMillis) {
    if (epochMillis == null || epochMillis <= 0L) {
      return null;
    }
    return LAST_SEEN_FORMAT.format(Instant.ofEpochMilli(epochMillis));
  }

  private Long trendPct(long current, long previous) {
    if (previous <= 0L) {
      return current > 0L ? 100L : null;
    }
    return Math.round(((double) (current - previous) / (double) previous) * 100d);
  }
}
