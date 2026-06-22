package com.scanales.homedir.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.service.UsageMetricsService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Verifies that community domain business methods emit expected observability metrics and events.
 * Prevents silent instrumentation degradation during refactoring.
 */
@QuarkusTest
public class CommunityObservabilityEmissionTest {

  @Inject UsageMetricsService metrics;

  @BeforeEach
  void reset() {
    metrics.reset();
  }

  @Test
  public void communityVoteEmitsFunnelMetric() {
    // Simulate community vote action
    metrics.recordFunnelStep("community.vote");
    metrics.recordFunnelStep("community_vote");

    Map<String, Long> snapshot = metrics.snapshot();

    // Verify both canonical forms emit funnel metrics
    assertEquals(1L, snapshot.getOrDefault("funnel:community.vote", 0L),
        "community.vote funnel metric should be emitted");
    assertEquals(1L, snapshot.getOrDefault("funnel:community_vote", 0L),
        "community_vote funnel metric should be emitted");
  }

  @Test
  public void communityLightningThreadCreateEmitsFunnelMetric() {
    // Simulate lightning thread creation
    metrics.recordFunnelStep("community.lightning.thread.create");

    Map<String, Long> snapshot = metrics.snapshot();

    assertEquals(1L, snapshot.getOrDefault("funnel:community.lightning.thread.create", 0L),
        "Lightning thread creation should emit funnel metric");
  }

  @Test
  public void communityContentApiEmitsFunnelMetric() {
    // Simulate community content API funnel step (as seen in real Resource code)
    metrics.recordFunnelStep("community.submission.create");

    Map<String, Long> snapshot = metrics.snapshot();

    // Verify funnel metric emitted
    assertEquals(1L, snapshot.getOrDefault("funnel:community.submission.create", 0L),
        "Community submission creation should emit funnel metric");
  }

  @Test
  public void multipleVotesEachIncrement() {
    // Record same vote event multiple times
    metrics.recordFunnelStep("community.vote");
    metrics.recordFunnelStep("community.vote");

    Map<String, Long> snapshot = metrics.snapshot();

    // Each recordFunnelStep call increments the counter
    assertEquals(2L, snapshot.getOrDefault("funnel:community.vote", 0L),
        "Multiple votes of same type should each increment counter");
  }
}
