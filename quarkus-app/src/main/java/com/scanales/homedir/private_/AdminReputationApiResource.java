package com.scanales.homedir.private_;

import com.scanales.homedir.reputation.ReputationEngineService;
import com.scanales.homedir.reputation.ReputationEventRecord;
import com.scanales.homedir.reputation.ReputationFeatureFlags;
import com.scanales.homedir.reputation.ReputationPhase0BaselineService;
import com.scanales.homedir.reputation.ReputationShadowReadService;
import com.scanales.homedir.reputation.ReputationWebVitalsHistoryService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.util.AdminUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Hidden admin API for phase-0 Reputation Hub baseline and taxonomy checks. */
@Path("/api/private/admin/reputation")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AdminReputationApiResource {
  @Inject SecurityIdentity identity;

  @ConfigProperty(name = "reputation.ga.min-route-samples", defaultValue = "20")
  long gaMinRouteSamples;

  @ConfigProperty(name = "reputation.ga.min-stable-windows", defaultValue = "3")
  long gaMinStableWindows;

  @ConfigProperty(name = "reputation.ga.min-route-page-views", defaultValue = "10")
  long gaMinRoutePageViews;

  @ConfigProperty(name = "reputation.ga.min-public-profile-opens", defaultValue = "5")
  long gaMinPublicProfileOpens;

  @ConfigProperty(name = "reputation.ga.min-board-profile-opens", defaultValue = "5")
  long gaMinBoardProfileOpens;

  @ConfigProperty(name = "reputation.ga.min-feedback-signals", defaultValue = "5")
  long gaMinFeedbackSignals;

  @ConfigProperty(name = "reputation.ga.min-recognition-signals", defaultValue = "5")
  long gaMinRecognitionSignals;

  @ConfigProperty(name = "reputation.ga.min-recognition-validators", defaultValue = "3")
  long gaMinRecognitionValidators;

  @ConfigProperty(name = "reputation.ga.recognition-window-days", defaultValue = "7")
  long gaRecognitionWindowDays;

  @Inject ReputationPhase0BaselineService baselineService;
  @Inject ReputationFeatureFlags reputationFeatureFlags;
  @Inject ReputationEngineService reputationEngineService;
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
    long hubPageViews = pageViews(snapshot, "/comunidad/reputation-hub", "/community/reputation-hub");
    long howPageViews =
        pageViews(snapshot, "/comunidad/reputation-hub/how", "/community/reputation-hub/how");
    long publicProfileOpens =
        maxCounter(snapshot, "funnel:profile.public.open", "funnel:profile_public_open");
    long boardProfileOpens = snapshot.getOrDefault("funnel:board_profile_open", 0L);
    long feedbackSignals = maxCounter(snapshot, "funnel:community_vote", "funnel:community.vote");
    ReputationFeatureFlags.Flags flags = reputationFeatureFlags.snapshot();
    boolean recognitionGateEnabled = flags.engineEnabled() && flags.recognitionEnabled();
    RecognitionSignalStats recognitionStats =
        recognitionGateEnabled
            ? collectRecentRecognitionSignalStats(gaRecognitionWindowDays)
            : new RecognitionSignalStats(0L, 0L);
    long recognitionSignals = recognitionStats.signals();
    long recognitionValidators = recognitionStats.validators();
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
        "traffic",
        Map.of(
            "minRoutePageViews", gaMinRoutePageViews,
            "routes",
                Map.of(
                    "hub", hubPageViews,
                    "how", howPageViews)));
    payload.put(
        "activityLoop",
        Map.of(
            "minimums",
                Map.of(
                "publicProfileOpens", gaMinPublicProfileOpens,
                "boardProfileOpens", gaMinBoardProfileOpens,
                "feedbackSignals", gaMinFeedbackSignals),
            "current",
                Map.of(
                    "publicProfileOpens", publicProfileOpens,
                    "boardProfileOpens", boardProfileOpens,
                    "feedbackSignals", feedbackSignals)));
    payload.put(
        "recognitionLoop",
        Map.of(
            "enabled", recognitionGateEnabled,
            "minimums",
                Map.of(
                    "recognitionSignals", gaMinRecognitionSignals,
                    "recognitionValidators", gaMinRecognitionValidators),
            "current",
                Map.of(
                    "recognitionSignals", recognitionSignals,
                    "recognitionValidators", recognitionValidators,
                    "windowDays", Math.max(1L, gaRecognitionWindowDays))));
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
    payload.put(
        "gaReadiness",
        gaReadinessPayload(
            hubAssessment,
            howAssessment,
            hubPageViews,
            howPageViews,
            publicProfileOpens,
            boardProfileOpens,
            feedbackSignals,
            recognitionGateEnabled,
            recognitionSignals,
            recognitionValidators,
            trend));
    return Response.ok(payload).build();
  }

  private RecognitionSignalStats collectRecentRecognitionSignalStats(long windowDays) {
    long safeWindowDays = Math.max(1L, windowDays);
    Instant threshold = Instant.now().minus(Duration.ofDays(safeWindowDays));
    Map<String, ReputationEventRecord> eventsById = reputationEngineService.snapshot().eventsById();
    if (eventsById == null || eventsById.isEmpty()) {
      return new RecognitionSignalStats(0L, 0L);
    }
    long count = 0L;
    Set<String> validators = new LinkedHashSet<>();
    for (ReputationEventRecord event : eventsById.values()) {
      if (event == null || event.createdAt() == null) {
        continue;
      }
      if (event.createdAt().isBefore(threshold)) {
        continue;
      }
      if (isRecognitionSignal(event)) {
        count++;
        String validator = normalizeUser(event.validatedByUserId());
        if (validator != null) {
          validators.add(validator);
        }
      }
    }
    return new RecognitionSignalStats(count, validators.size());
  }

  private boolean isRecognitionSignal(ReputationEventRecord event) {
    if (event == null) {
      return false;
    }
    String validationType = normalizeToken(event.validationType());
    if ("recommended".equals(validationType)
        || "helpful".equals(validationType)
        || "standout".equals(validationType)) {
      return true;
    }
    String eventType = normalizeToken(event.eventType());
    return "content_recommended".equals(eventType)
        || "peer_help_acknowledged".equals(eventType)
        || "contribution_highlighted".equals(eventType);
  }

  private String normalizeToken(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]", "_");
    return normalized.isBlank() ? null : normalized;
  }

  private String normalizeUser(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  private long maxCounter(Map<String, Long> snapshot, String... keys) {
    long best = 0L;
    for (String key : keys) {
      best = Math.max(best, snapshot.getOrDefault(key, 0L));
    }
    return best;
  }

  private long pageViews(Map<String, Long> snapshot, String... routes) {
    long total = 0L;
    for (String route : routes) {
      total += snapshot.getOrDefault("page_view:" + route, 0L);
    }
    return total;
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
      long hubPageViews,
      long howPageViews,
      long publicProfileOpens,
      long boardProfileOpens,
      long feedbackSignals,
      boolean recognitionGateEnabled,
      long recognitionSignals,
      long recognitionValidators,
      ReputationWebVitalsHistoryService.TrendWindow trend) {
    String hubTrendStatus = trendStatus(trend, "hub");
    String howTrendStatus = trendStatus(trend, "how");
    ReputationWebVitalsHistoryService.RouteStability hubStability = stabilityForRoute(trend, "hub");
    ReputationWebVitalsHistoryService.RouteStability howStability = stabilityForRoute(trend, "how");

    List<String> blockers = new ArrayList<>();
    Map<String, Object> blockerDetails = new LinkedHashMap<>();
    Set<String> recommendedActionSet = new LinkedHashSet<>();
    Map<String, String> blockerToAction = new LinkedHashMap<>();

    if (hubAssessment.samples() < gaMinRouteSamples || howAssessment.samples() < gaMinRouteSamples) {
      registerBlocker(
          blockers,
          blockerDetails,
          recommendedActionSet,
          blockerToAction,
          "insufficient_samples",
          "insufficient_samples",
          Map.of(
              "required", gaMinRouteSamples,
              "routes",
                  Map.of(
                      "hub", hubAssessment.samples(),
                      "how", howAssessment.samples())),
          "collect_more_webvitals_samples");
    }

    if (hubPageViews < gaMinRoutePageViews || howPageViews < gaMinRoutePageViews) {
      registerBlocker(
          blockers,
          blockerDetails,
          recommendedActionSet,
          blockerToAction,
          "insufficient_live_traffic",
          "insufficient_live_traffic",
          Map.of(
              "required", gaMinRoutePageViews,
              "routes",
                  Map.of(
                      "hub", hubPageViews,
                      "how", howPageViews)),
          "increase_hub_route_adoption");
    }

    if (publicProfileOpens < gaMinPublicProfileOpens
        || boardProfileOpens < gaMinBoardProfileOpens
        || feedbackSignals < gaMinFeedbackSignals) {
      registerBlocker(
          blockers,
          blockerDetails,
          recommendedActionSet,
          blockerToAction,
          "insufficient_activity_loop_signals",
          "insufficient_activity_loop_signals",
          Map.of(
              "required",
                  Map.of(
                      "publicProfileOpens", gaMinPublicProfileOpens,
                      "boardProfileOpens", gaMinBoardProfileOpens,
                      "feedbackSignals", gaMinFeedbackSignals),
              "current",
                  Map.of(
                      "publicProfileOpens", publicProfileOpens,
                      "boardProfileOpens", boardProfileOpens,
                      "feedbackSignals", feedbackSignals)),
          "drive_profile_feedback_cycle");
    }

    if (recognitionGateEnabled && recognitionSignals < gaMinRecognitionSignals) {
      registerBlocker(
          blockers,
          blockerDetails,
          recommendedActionSet,
          blockerToAction,
          "insufficient_recognition_signals",
          "insufficient_recognition_signals",
          Map.of(
              "required", gaMinRecognitionSignals,
              "current", recognitionSignals,
              "windowDays", Math.max(1L, gaRecognitionWindowDays)),
          "increase_peer_recognition_activity");
    }

    if (recognitionGateEnabled && recognitionValidators < gaMinRecognitionValidators) {
      registerBlocker(
          blockers,
          blockerDetails,
          recommendedActionSet,
          blockerToAction,
          "insufficient_recognition_validators",
          "insufficient_recognition_validators",
          Map.of(
              "required", gaMinRecognitionValidators,
              "current", recognitionValidators,
              "windowDays", Math.max(1L, gaRecognitionWindowDays)),
          "expand_recognition_validator_pool");
    }

    if (trend == null || !trend.snapshotRecorded()) {
      registerBlocker(
          blockers,
          blockerDetails,
          recommendedActionSet,
          blockerToAction,
          "stale_window_data",
          "stale_window_data",
          Map.of(
              "snapshotRecorded", trend != null && trend.snapshotRecorded()),
          "verify_web_vitals_ingestion");
    }

    if (hubStability.consecutiveNonWorsening() < gaMinStableWindows
        || howStability.consecutiveNonWorsening() < gaMinStableWindows) {
      registerBlocker(
          blockers,
          blockerDetails,
          recommendedActionSet,
          blockerToAction,
          "insufficient_stability_windows",
          "insufficient_stability_windows",
          Map.of(
              "required", gaMinStableWindows,
              "routes",
                  Map.of(
                      "hub", hubStability.consecutiveNonWorsening(),
                      "how", howStability.consecutiveNonWorsening())),
          "observe_more_stable_windows");
    }

    Set<String> criticalStatuses = Set.of("critical");
    if (criticalStatuses.contains(hubAssessment.status()) || criticalStatuses.contains(howAssessment.status())) {
      registerBlocker(
          blockers,
          blockerDetails,
          recommendedActionSet,
          blockerToAction,
          "critical_route_status",
          "critical_route_status",
          Map.of(
              "routes",
                  Map.of(
                      "hub", hubAssessment.status(),
                      "how", howAssessment.status())),
          "improve_critical_route_performance");
    }

    if ("worsening".equals(hubTrendStatus) || "worsening".equals(howTrendStatus)) {
      registerBlocker(
          blockers,
          blockerDetails,
          recommendedActionSet,
          blockerToAction,
          "active_worsening_trend",
          "active_worsening_trend",
          Map.of(
              "routes",
                  Map.of(
                      "hub", hubTrendStatus,
                      "how", howTrendStatus)),
          "triage_worsening_route");
    }

    List<String> orderedBlockers = orderedBlockers(blockers);
    List<String> orderedRecommendedActions = orderedRecommendedActions(orderedBlockers, blockerToAction);
    List<Map<String, Object>> actionPlan = buildActionPlan(orderedBlockers, blockerToAction);
    String primaryBlocker = orderedBlockers.isEmpty() ? "none" : orderedBlockers.get(0);
    String primaryAction =
        orderedBlockers.isEmpty()
            ? "none"
            : blockerToAction.getOrDefault(primaryBlocker, "investigate_blocker");
    String status = orderedBlockers.isEmpty() ? "ready" : "not_ready";
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("status", status);
    payload.put("minRouteSamples", gaMinRouteSamples);
    payload.put("minStableWindows", gaMinStableWindows);
    payload.put("minRoutePageViews", gaMinRoutePageViews);
    payload.put("minPublicProfileOpens", gaMinPublicProfileOpens);
    payload.put("minBoardProfileOpens", gaMinBoardProfileOpens);
    payload.put("minFeedbackSignals", gaMinFeedbackSignals);
    payload.put("minRecognitionSignals", gaMinRecognitionSignals);
    payload.put("minRecognitionValidators", gaMinRecognitionValidators);
    payload.put("recognitionWindowDays", Math.max(1L, gaRecognitionWindowDays));
    payload.put("recognitionGateEnabled", recognitionGateEnabled);
    payload.put("recognitionSignals", recognitionSignals);
    payload.put("recognitionValidators", recognitionValidators);
    payload.put("snapshotRecorded", trend != null && trend.snapshotRecorded());
    payload.put("blockers", orderedBlockers);
    payload.put("primaryBlocker", primaryBlocker);
    payload.put("primaryAction", primaryAction);
    payload.put("recommendedActions", orderedRecommendedActions);
    payload.put("actionPlan", actionPlan);
    payload.put("blockerDetails", Map.copyOf(blockerDetails));
    payload.put(
        "activityLoop",
        Map.of(
            "publicProfileOpens", publicProfileOpens,
            "boardProfileOpens", boardProfileOpens,
            "feedbackSignals", feedbackSignals));
    payload.put(
        "recognitionLoop",
        Map.of(
            "recognitionSignals", recognitionSignals,
            "recognitionValidators", recognitionValidators,
            "windowDays", Math.max(1L, gaRecognitionWindowDays),
            "enabled", recognitionGateEnabled));
    payload.put(
        "stability",
        Map.of(
            "hub",
                Map.of(
                    "observedWindows", hubStability.observedWindows(),
                    "consecutiveNonWorsening", hubStability.consecutiveNonWorsening()),
            "how",
                Map.of(
                    "observedWindows", howStability.observedWindows(),
                    "consecutiveNonWorsening", howStability.consecutiveNonWorsening())));
    payload.put(
        "routes",
        Map.of(
            "hub",
                Map.of(
                    "samples", hubAssessment.samples(),
                    "livePageViews", hubPageViews,
                    "assessmentStatus", hubAssessment.status(),
                    "trendStatus", hubTrendStatus),
            "how",
                Map.of(
                    "samples", howAssessment.samples(),
                    "livePageViews", howPageViews,
                    "assessmentStatus", howAssessment.status(),
                    "trendStatus", howTrendStatus)));
    return Map.copyOf(payload);
  }

  private void registerBlocker(
      List<String> blockers,
      Map<String, Object> blockerDetails,
      Set<String> recommendedActionSet,
      Map<String, String> blockerToAction,
      String blocker,
      String detailKey,
      Map<String, Object> details,
      String action) {
    blockers.add(blocker);
    blockerDetails.put(detailKey, details);
    recommendedActionSet.add(action);
    blockerToAction.put(blocker, action);
  }

  private List<String> orderedBlockers(List<String> blockers) {
    List<String> ordered = new ArrayList<>(blockers);
    ordered.sort(Comparator.comparingInt(this::blockerPriority).reversed().thenComparing(String::compareTo));
    return List.copyOf(ordered);
  }

  private List<String> orderedRecommendedActions(
      List<String> orderedBlockers, Map<String, String> blockerToAction) {
    List<String> actions = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (String blocker : orderedBlockers) {
      String action = blockerToAction.getOrDefault(blocker, "investigate_blocker");
      if (seen.add(action)) {
        actions.add(action);
      }
    }
    return List.copyOf(actions);
  }

  private List<Map<String, Object>> buildActionPlan(
      List<String> orderedBlockers, Map<String, String> blockerToAction) {
    List<Map<String, Object>> plan = new ArrayList<>();
    for (String blocker : orderedBlockers) {
      plan.add(
          Map.of(
              "blocker", blocker,
              "priority", blockerPriority(blocker),
              "action", blockerToAction.getOrDefault(blocker, "investigate_blocker"),
              "status", "pending"));
    }
    return List.copyOf(plan);
  }

  private int blockerPriority(String blocker) {
    if (blocker == null) {
      return 0;
    }
    return switch (blocker) {
      case "stale_window_data" -> 100;
      case "critical_route_status" -> 90;
      case "active_worsening_trend" -> 80;
      case "insufficient_live_traffic" -> 70;
      case "insufficient_recognition_signals" -> 65;
      case "insufficient_recognition_validators" -> 64;
      case "insufficient_activity_loop_signals" -> 60;
      case "insufficient_stability_windows" -> 50;
      case "insufficient_samples" -> 40;
      default -> 10;
    };
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

  private ReputationWebVitalsHistoryService.RouteStability stabilityForRoute(
      ReputationWebVitalsHistoryService.TrendWindow trend, String route) {
    if (trend == null || trend.stability() == null) {
      return new ReputationWebVitalsHistoryService.RouteStability(0L, 0L);
    }
    ReputationWebVitalsHistoryService.RouteStability routeStability = trend.stability().get(route);
    if (routeStability == null) {
      return new ReputationWebVitalsHistoryService.RouteStability(0L, 0L);
    }
    return routeStability;
  }

  private record RouteWebVitals(
      long samples, Map<String, Long> devices, Map<String, Long> lcp, Map<String, Long> inp) {}

  private record MetricAssessment(int score, String status) {}

  private record RouteAssessment(
      String route, long samples, int lcpScore, int inpScore, int overallScore, String status) {}

  private record RecognitionSignalStats(long signals, long validators) {}
}
