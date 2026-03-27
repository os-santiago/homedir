package com.scanales.homedir.private_;

import com.scanales.homedir.reputation.ReputationPhase0BaselineService;
import com.scanales.homedir.reputation.ReputationShadowReadService;
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
import java.util.HashMap;
import java.util.Map;

/** Hidden admin API for phase-0 Reputation Hub baseline and taxonomy checks. */
@Path("/api/private/admin/reputation")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AdminReputationApiResource {

  @Inject SecurityIdentity identity;

  @Inject ReputationPhase0BaselineService baselineService;
  @Inject ReputationShadowReadService shadowReadService;
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

    long totalSamples = hub.samples() + how.samples();
    Map<String, Object> payload = new HashMap<>();
    payload.put("generatedAt", System.currentTimeMillis());
    payload.put("totalSamples", totalSamples);
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

  private record RouteWebVitals(
      long samples, Map<String, Long> devices, Map<String, Long> lcp, Map<String, Long> inp) {}
}
