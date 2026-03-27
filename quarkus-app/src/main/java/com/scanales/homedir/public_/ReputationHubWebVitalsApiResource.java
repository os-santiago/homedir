package com.scanales.homedir.public_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.homedir.reputation.ReputationFeatureFlags;
import com.scanales.homedir.service.UsageMetricsService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/api/community/reputation/web-vitals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReputationHubWebVitalsApiResource {

  @Inject ReputationFeatureFlags reputationFeatureFlags;
  @Inject UsageMetricsService metrics;

  @POST
  @PermitAll
  public Response track(WebVitalsRequest request) {
    ReputationFeatureFlags.Flags flags = reputationFeatureFlags.snapshot();
    if (!flags.engineEnabled() || !flags.hubUiEnabled()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    if (request == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_body")).build();
    }

    String route = normalizeRoute(request.route());
    if (route == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "invalid_route"))
          .build();
    }
    Integer lcpMs = sanitizeMs(request.lcpMs());
    Integer inpMs = sanitizeMs(request.inpMs());
    if (lcpMs == null && inpMs == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "invalid_metrics"))
          .build();
    }

    String device = normalizeDevice(request.viewportWidth());
    metrics.recordFunnelStep("reputation." + route + ".webvitals.sample");
    metrics.recordFunnelStep("reputation." + route + ".webvitals.device." + device);
    if (lcpMs != null) {
      metrics.recordFunnelStep("reputation." + route + ".webvitals.lcp." + lcpBucket(lcpMs));
    }
    if (inpMs != null) {
      metrics.recordFunnelStep("reputation." + route + ".webvitals.inp." + inpBucket(inpMs));
    }
    return Response.accepted().entity(Map.of("ok", true)).build();
  }

  private String normalizeRoute(String route) {
    if (route == null) {
      return null;
    }
    String normalized = route.trim().toLowerCase();
    if ("hub".equals(normalized) || "how".equals(normalized)) {
      return normalized;
    }
    return null;
  }

  private String normalizeDevice(Integer viewportWidth) {
    if (viewportWidth == null || viewportWidth <= 0) {
      return "unknown";
    }
    return viewportWidth <= 980 ? "mobile" : "desktop";
  }

  private Integer sanitizeMs(Double value) {
    if (value == null || !Double.isFinite(value)) {
      return null;
    }
    int rounded = (int) Math.round(value);
    if (rounded < 0 || rounded > 60000) {
      return null;
    }
    return rounded;
  }

  private String lcpBucket(int lcpMs) {
    if (lcpMs <= 2500) {
      return "good";
    }
    if (lcpMs <= 4000) {
      return "needs_improvement";
    }
    return "poor";
  }

  private String inpBucket(int inpMs) {
    if (inpMs <= 200) {
      return "good";
    }
    if (inpMs <= 500) {
      return "needs_improvement";
    }
    return "poor";
  }

  public record WebVitalsRequest(
      String route,
      @JsonProperty("lcp_ms") Double lcpMs,
      @JsonProperty("inp_ms") Double inpMs,
      @JsonProperty("viewport_width") Integer viewportWidth) {}
}
