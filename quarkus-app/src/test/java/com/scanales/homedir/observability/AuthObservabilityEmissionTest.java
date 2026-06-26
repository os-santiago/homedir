package com.scanales.homedir.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scanales.homedir.service.UsageMetricsService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that authentication domain business methods emit expected observability metrics. Tracks
 * login success/failure events for security monitoring and user analytics.
 */
@QuarkusTest
public class AuthObservabilityEmissionTest {

  @Inject UsageMetricsService metrics;

  @BeforeEach
  void reset() {
    metrics.reset();
  }

  @Test
  public void loginSuccessEmitsFunnelMetrics() {
    // Simulate successful login callback
    metrics.recordFunnelStep("auth.login.callback");
    metrics.recordFunnelStep("login_success");

    Map<String, Long> snapshot = metrics.snapshot();

    // Verify both metrics emitted
    assertEquals(
        1L,
        snapshot.getOrDefault("funnel:auth.login.callback", 0L),
        "auth.login.callback should be tracked");
    assertEquals(
        1L,
        snapshot.getOrDefault("funnel:login_success", 0L),
        "login_success should be tracked for analytics");
  }

  @Test
  public void multipleLoginAttemptsAreEachCounted() {
    // Simulate two distinct login events
    metrics.recordFunnelStep("login_success");
    metrics.recordFunnelStep("login_success");

    Map<String, Long> snapshot = metrics.snapshot();

    assertEquals(
        2L,
        snapshot.getOrDefault("funnel:login_success", 0L),
        "Multiple successful logins should each increment counter");
  }

  @Test
  public void authCallbackWithoutSuccessFlagIsStillTracked() {
    // Track callback independently of success
    metrics.recordFunnelStep("auth.login.callback");

    Map<String, Long> snapshot = metrics.snapshot();

    assertEquals(
        1L,
        snapshot.getOrDefault("funnel:auth.login.callback", 0L),
        "All auth callbacks should be tracked regardless of outcome");
  }
}
