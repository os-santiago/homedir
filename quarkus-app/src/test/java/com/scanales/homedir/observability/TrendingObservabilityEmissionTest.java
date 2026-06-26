package com.scanales.homedir.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.service.UsageMetricsService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that trending/popular content views emit expected observability metrics. Tracks user
 * engagement with talk views and event registrations.
 */
@QuarkusTest
public class TrendingObservabilityEmissionTest {

  @Inject UsageMetricsService metrics;

  @BeforeEach
  void reset() {
    metrics.reset();
  }

  @Test
  public void talkViewEmitsMetric() {
    // Simulate talk view
    metrics.recordTalkView("talk123", "session456", "Mozilla/5.0 (Windows NT 10.0)");

    Map<String, Long> snapshot = metrics.snapshot();

    // Verify talk view metric
    assertEquals(
        1L,
        snapshot.getOrDefault("talk_view:talk123", 0L),
        "Talk view should emit metric with talk ID");
  }

  @Test
  public void talkViewDeduplicatesSameSession() {
    // Same session viewing same talk multiple times
    metrics.recordTalkView("talk123", "session456", "Mozilla/5.0");
    metrics.recordTalkView("talk123", "session456", "Mozilla/5.0");
    metrics.recordTalkView("talk123", "session456", "Mozilla/5.0");

    Map<String, Long> snapshot = metrics.snapshot();

    // Should only count once per session
    assertEquals(
        1L,
        snapshot.getOrDefault("talk_view:talk123", 0L),
        "Duplicate views from same session should be deduplicated");

    Map<String, Long> discards = metrics.getDiscarded();
    assertEquals(
        2L,
        discards.getOrDefault("dedupe", 0L),
        "Deduplicated views should be tracked as discards");
  }

  @Test
  public void botTalkViewsAreDiscarded() {
    // Simulate bot user agent
    metrics.recordTalkView("talk123", "bot_session", "Googlebot/2.1");

    Map<String, Long> snapshot = metrics.snapshot();

    // Bot views should not appear in metrics
    assertTrue(!snapshot.containsKey("talk_view:talk123"), "Bot views should be filtered out");

    Map<String, Long> discards = metrics.getDiscarded();
    assertEquals(1L, discards.getOrDefault("bot", 0L), "Bot views should be tracked as discards");
  }

  @Test
  public void eventViewIncrementsCentralCounter() {
    // Simulate multiple event views from different sessions
    metrics.recordEventView("devopsdays", "session1", "Mozilla/5.0");
    metrics.recordEventView("devopsdays", "session2", "Mozilla/5.0");

    Map<String, Long> snapshot = metrics.snapshot();

    // recordEventView uses "event_view:" prefix
    assertEquals(
        2L,
        snapshot.getOrDefault("event_view:devopsdays", 0L),
        "Each event view should increment the event_view counter");
  }

  @Test
  public void talkRegistrationEmitsMetric() {
    // Simulate talk registration
    metrics.recordTalkRegister(
        "talk789", java.util.List.of(), "Mozilla/5.0", "John Doe", "john@example.com");

    Map<String, Long> snapshot = metrics.snapshot();

    assertEquals(
        1L,
        snapshot.getOrDefault("talk_register:talk789", 0L),
        "Talk registration should emit metric");
    assertEquals(
        1,
        metrics.getRegistrants("talk789").size(),
        "Registrant should be tracked in registrations map");
  }
}
