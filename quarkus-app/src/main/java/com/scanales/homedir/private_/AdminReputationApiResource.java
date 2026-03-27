package com.scanales.homedir.private_;

import com.scanales.homedir.reputation.ReputationPhase0BaselineService;
import com.scanales.homedir.reputation.ReputationShadowReadService;
import com.scanales.homedir.reputation.ReputationWebVitalsHistoryService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Hidden admin API for phase-0 Reputation Hub baseline and taxonomy checks. */
@Path("/api/private/admin/reputation")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AdminReputationApiResource {
  private static final long GA_MIN_ROUTE_SAMPLES = 20L;

  @Inject SecurityIdentity identity;

  @Inject ReputationPhase0BaselineService baselineService;
  @Inject ReputationShadowReadService shadowReadService;
  @Inject ReputationWebVitalsHistoryService webVitalsHistoryService;
  @Inject UsageMetricsService usageMetricsService;

  @GET
  @Path("phase0")
  public Response phase0() {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return Response.ok(baselineService.snapshot()).build();
  }

  @GET
  @Path("phase2/diagnostics")
  public Response phase2Diagnostics() {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return shadowReadService
        .diagnostics()
        .map(payload -> Response.ok(payload).build())
        .orElseGet(
            () ->
                Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "reputation_shadow_read_disabled"))
                    .build());
  }

  @GET
  @Path("phase2/user/{userId}")
  public Response phase2User(@PathParam("userId") String userId, @QueryParam("limit") Integer limit) {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return shadowReadService
        .user(userId, limit)
        .map(payload -> Response.ok(payload).build())
        .orElseGet(
            () ->
                Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "reputation_shadow_read_disabled"))
                    .build());
  }

  @GET
  @Path("web-vitals")
  public Response webVitals() {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    Map<String, Long> snapshot = usageMetricsService.snapshot();
    RouteWebVitals hub = summarizeRoute(snapshot, "hub");
    RouteWebVitals how = summarizeRoute(snapshot, "how");
    RouteAssessment hubAssessment = assessRoute("hub", hub);
    RouteAssessment howAssessment = assessRoute("how", how);
    ReputationWebVitalsHistoryService.TrendWindow trend =
        webVitalsHistoryService.recordAndTrend(
            Map.of(
                "hub", new ReputationWebVitalsHistoryService.RouteTotals(hub.samples(), hub.lcp(), hub.inp()),
                "how",
                    new ReputationWebVitalsHistoryService.RouteTotals(
                        how.samples(), how.lcp(), how.inp())));

    long totalSamples = hub.samples() + how.samples();
    Map<String, Object> payload = new HashMap<>();
    payload.put("generatedAt", System.currentTimeMillis());
    payload.put("totalSamples", totalSamples);
    payload.put("nextFocusRoute", nextFocusRoute(hubAssessment, howAssessment));
    payload.put(
        "routes",
        Map.of(
            "hub",
            Map.of(
                "samples", hub.samples(),
                "devices", hub.devices(),
                "lcp", hub.lcp(),
                "inp", hub.inp()),
            "how",
            Map.of(
                "samples", how.samples(),
                "devices", how.devices(),
                "lcp", how.lcp(),
                "inp", how.inp())));
    payload.put(
        "assessments",
        Map.of(
            "hub", assessmentPayload(hubAssessment),
            "how", assessmentPayload(howAssessment)));
    payload.put(
        "trend",
        Map.of(
            "windowSize", trend.windowSize(),
            "routes",
                Map.of(
                    "hub", trendPayload(trend.routes().get("hub")),
                    "how", trendPayload(trend.routes().get("how")))));
    payload.put("gaReadiness", gaReadinessPayload(hubAssessment, howAssessment, trend));
    return Response.ok(payload).build();
  }

  private RouteWebVitals summarizeRoute(Map<String, Long> snapshot, String route) {
    String base = "funnel:reputation." + route + ".webvitals.";
    long samples = snapshot.getOrDefault(base + "sample", 0L);
    Map<String, Long> devices = countByToken(snapshot, base + "device.", "mobile", "desktop", "unknown");
    Map<String, Long> lcp = countByToken(snapshot, base + "lcp.", "good", "needs_improvement", "poor");
    Map<String, Long> inp = countByToken(snapshot, base + "inp.", "good", "needs_improvement", "poor");
    return new RouteWebVitals(samples, devices, lcp, inp);
  }

  private Map<String, Long> countByToken(Map<String, Long> snapshot, String prefix, String... tokens) {
    Map<String, Long> values = new HashMap<>();
    for (String token : tokens) {
      values.put(token, snapshot.getOrDefault(prefix + token, 0L));
    }
    return Map.copyOf(values);
  }

  private RouteAssessment assessRoute(String route, RouteWebVitals vitals) {
    MetricAssessment lcp = assessMetric(vitals.lcp());
    MetricAssessment inp = assessMetric(vitals.inp());

    int scoreCount = 0;
    int scoreSum = 0;
    if (lcp.score() >= 0) {
      scoreCount++;
      scoreSum += lcp.score();
    }
    if (inp.score() >= 0) {
      scoreCount++;
      scoreSum += inp.score();
    }
    int overallScore = scoreCount == 0 ? -1 : Math.round((float) scoreSum / scoreCount);
    String status = mergeStatus(lcp.status(), inp.status());
    return new RouteAssessment(route, vitals.samples(), lcp.score(), inp.score(), overallScore, status);
  }

  private MetricAssessment assessMetric(Map<String, Long> buckets) {
    long good = buckets.getOrDefault("good", 0L);
    long needs = buckets.getOrDefault("needs_improvement", 0L);
    long poor = buckets.getOrDefault("poor", 0L);
    long total = good + needs + poor;
    if (total <= 0) {
      return new MetricAssessment(-1, "unknown");
    }
    long weighted = good * 100L + needs * 60L + poor * 20L;
    int score = Math.toIntExact(Math.round((double) weighted / (double) total));
    String status;
    if (poor * 5L >= total || score < 55) {
      status = "critical";
    } else if (poor * 10L >= total || score < 75 || needs * 3L >= total) {
      status = "watch";
    } else {
      status = "healthy";
    }
    return new MetricAssessment(score, status);
  }

  private String mergeStatus(String first, String second) {
    if ("critical".equals(first) || "critical".equals(second)) {
      return "critical";
    }
    if ("watch".equals(first) || "watch".equals(second)) {
      return "watch";
    }
    if ("healthy".equals(first) && "healthy".equals(second)) {
      return "healthy";
    }
    return "unknown";
  }

  private String nextFocusRoute(RouteAssessment... assessments) {
    List<RouteAssessment> withSamples = new ArrayList<>();
    for (RouteAssessment assessment : assessments) {
      if (assessment != null && assessment.samples() > 0 && assessment.overallScore() >= 0) {
        withSamples.add(assessment);
      }
    }
    if (withSamples.isEmpty()) {
      return "none";
    }
    withSamples.sort(Comparator.comparingInt(RouteAssessment::overallScore));
    return withSamples.get(0).route();
  }

  private Map<String, Object> assessmentPayload(RouteAssessment assessment) {
    return Map.of(
        "samples", assessment.samples(),
        "lcpScore", assessment.lcpScore(),
        "inpScore", assessment.inpScore(),
        "overallScore", assessment.overallScore(),
        "status", assessment.status());
  }

  private Map<String, Object> trendPayload(ReputationWebVitalsHistoryService.RouteTrend trend) {
    if (trend == null) {
      return Map.of(
          "samplesDelta", 0L,
          "lcpPoorDelta", 0L,
          "lcpNeedsDelta", 0L,
          "inpPoorDelta", 0L,
          "inpNeedsDelta", 0L,
          "status", "insufficient_data");
    }
    return Map.of(
        "samplesDelta", trend.samplesDelta(),
        "lcpPoorDelta", trend.lcpPoorDelta(),
        "lcpNeedsDelta", trend.lcpNeedsDelta(),
        "inpPoorDelta", trend.inpPoorDelta(),
        "inpNeedsDelta", trend.inpNeedsDelta(),
        "status", trend.status());
  }

  private Map<String, Object> gaReadinessPayload(
      RouteAssessment hubAssessment,
      RouteAssessment howAssessment,
      ReputationWebVitalsHistoryService.TrendWindow trend) {
    String hubTrendStatus = trendStatus(trend, "hub");
    String howTrendStatus = trendStatus(trend, "how");

    List<String> blockers = new ArrayList<>();
    if (hubAssessment.samples() < GA_MIN_ROUTE_SAMPLES || howAssessment.samples() < GA_MIN_ROUTE_SAMPLES) {
      blockers.add("insufficient_samples");
    }

    Set<String> criticalStatuses = Set.of("critical");
    if (criticalStatuses.contains(hubAssessment.status()) || criticalStatuses.contains(howAssessment.status())) {
      blockers.add("critical_route_status");
    }

    if ("worsening".equals(hubTrendStatus) || "worsening".equals(howTrendStatus)) {
      blockers.add("active_worsening_trend");
    }

    String status = blockers.isEmpty() ? "ready" : "not_ready";
    return Map.of(
        "status", status,
        "minRouteSamples", GA_MIN_ROUTE_SAMPLES,
        "blockers", List.copyOf(blockers),
        "routes",
            Map.of(
                "hub",
                    Map.of(
                        "samples", hubAssessment.samples(),
                        "assessmentStatus", hubAssessment.status(),
                        "trendStatus", hubTrendStatus),
                "how",
                    Map.of(
                        "samples", howAssessment.samples(),
                        "assessmentStatus", howAssessment.status(),
                        "trendStatus", howTrendStatus)));
  }

  private String trendStatus(ReputationWebVitalsHistoryService.TrendWindow trend, String route) {
    if (trend == null || trend.routes() == null) {
      return "insufficient_data";
    }
    ReputationWebVitalsHistoryService.RouteTrend routeTrend = trend.routes().get(route);
    if (routeTrend == null) {
      return "insufficient_data";
    }
    return routeTrend.status();
  }

  private record RouteWebVitals(
      long samples, Map<String, Long> devices, Map<String, Long> lcp, Map<String, Long> inp) {}

  private record MetricAssessment(int score, String status) {}

  private record RouteAssessment(
      String route, long samples, int lcpScore, int inpScore, int overallScore, String status) {}
}
