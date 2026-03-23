package com.scanales.homedir.reputation;

import com.scanales.homedir.service.UsageMetricsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;

/** Phase-0 baseline snapshot using existing product signals without changing public UX. */
@ApplicationScoped
public class ReputationPhase0BaselineService {

  @Inject UsageMetricsService usageMetricsService;

  @Inject ReputationFeatureFlags featureFlags;

  public BaselineSnapshot snapshot() {
    Map<String, Long> counters = usageMetricsService.snapshot();
    return new BaselineSnapshot(
        System.currentTimeMillis(),
        featureFlags.snapshot(),
        sumPrefix(counters, "page_view:/comunidad/"),
        sumPrefix(counters, "page_view:/comunidad/board"),
        counters.getOrDefault("page_view:/private/profile", 0L),
        counters.getOrDefault("funnel:profile.public.open", 0L)
            + counters.getOrDefault("funnel:profile_public_open", 0L),
        counters.getOrDefault("funnel:board_profile_open", 0L),
        counters.getOrDefault("funnel:community.vote.recommended", 0L)
            + counters.getOrDefault("funnel:community.vote.must_see", 0L),
        List.of(
            "/comunidad/board",
            "/comunidad/board/{group}",
            "/comunidad/member/{group}/{id}",
            "/private/profile",
            "/u/{handle}"),
        ReputationEventTaxonomy.definitions());
  }

  static long sumPrefix(Map<String, Long> counters, String prefix) {
    if (counters == null || counters.isEmpty() || prefix == null || prefix.isBlank()) {
      return 0L;
    }
    long total = 0L;
    for (Map.Entry<String, Long> entry : counters.entrySet()) {
      String key = entry.getKey();
      Long value = entry.getValue();
      if (key != null && key.startsWith(prefix) && value != null && value > 0L) {
        total += value;
      }
    }
    return Math.max(0L, total);
  }

  public record BaselineSnapshot(
      long generatedAtMillis,
      ReputationFeatureFlags.Flags flags,
      long communityViews,
      long communityBoardViews,
      long profileViews,
      long publicProfileOpens,
      long boardProfileOpens,
      long recommendationSignals,
      List<String> routeInventory,
      List<ReputationEventTaxonomy.EventDefinition> taxonomy) {}
}
