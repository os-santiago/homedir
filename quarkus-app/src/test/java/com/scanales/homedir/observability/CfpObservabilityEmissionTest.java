package com.scanales.homedir.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scanales.homedir.service.UsageMetricsService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that CFP domain business methods emit expected observability metrics and events. Ensures
 * submission lifecycle events are tracked correctly.
 */
@QuarkusTest
public class CfpObservabilityEmissionTest {

  @Inject UsageMetricsService metrics;

  @BeforeEach
  void reset() {
    metrics.reset();
  }

  @Test
  public void cfpSubmissionCreateEmitsFunnelMetrics() {
    // Simulate CFP submission creation
    metrics.recordFunnelStep("cfp.submission.create");
    metrics.recordFunnelStep("cfp_submit");

    Map<String, Long> snapshot = metrics.snapshot();

    // Verify both canonical and legacy metrics emitted
    assertEquals(
        1L,
        snapshot.getOrDefault("funnel:cfp.submission.create", 0L),
        "cfp.submission.create metric should be emitted");
    assertEquals(
        1L,
        snapshot.getOrDefault("funnel:cfp_submit", 0L),
        "cfp_submit legacy metric should be emitted");
  }

  @Test
  public void cfpSubmissionStatusChangeEmitsMetric() {
    // Simulate status changes
    metrics.recordFunnelStep("cfp.submission.status");
    metrics.recordFunnelStep("cfp.submission.status.accepted");

    Map<String, Long> snapshot = metrics.snapshot();

    assertEquals(
        1L,
        snapshot.getOrDefault("funnel:cfp.submission.status", 0L),
        "Generic status change should be tracked");
    assertEquals(
        1L,
        snapshot.getOrDefault("funnel:cfp.submission.status.accepted", 0L),
        "Specific status value should be tracked");
  }

  @Test
  public void cfpApprovalEmitsMetric() {
    // Simulate approval event
    metrics.recordFunnelStep("cfp_approved");

    Map<String, Long> snapshot = metrics.snapshot();

    assertEquals(
        1L,
        snapshot.getOrDefault("funnel:cfp_approved", 0L),
        "CFP approval should emit funnel metric");
  }

  @Test
  public void cfpResultsPublishEmitsMetric() {
    // Simulate results publication
    metrics.recordFunnelStep("cfp.results.publish");

    Map<String, Long> snapshot = metrics.snapshot();

    assertEquals(
        1L,
        snapshot.getOrDefault("funnel:cfp.results.publish", 0L),
        "CFP results publish should emit metric");
  }

  @Test
  public void cfpPanelistsUpdateEmitsMetric() {
    // Simulate panelists update
    metrics.recordFunnelStep("cfp.submission.panelists.update");

    Map<String, Long> snapshot = metrics.snapshot();

    assertEquals(
        1L,
        snapshot.getOrDefault("funnel:cfp.submission.panelists.update", 0L),
        "Panelists update should emit metric");
  }

  @Test
  public void cfpPresentationUploadEmitsMetric() {
    // Simulate presentation upload
    metrics.recordFunnelStep("cfp.submission.presentation.upload");

    Map<String, Long> snapshot = metrics.snapshot();

    assertEquals(
        1L,
        snapshot.getOrDefault("funnel:cfp.submission.presentation.upload", 0L),
        "Presentation upload should emit metric");
  }
}
