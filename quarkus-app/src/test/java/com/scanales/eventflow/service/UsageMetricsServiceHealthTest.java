package com.scanales.eventflow.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class UsageMetricsServiceHealthTest {

  @Inject UsageMetricsService metrics;

  @BeforeEach
  void reset() {
    metrics.reset();
  }

  @Test
  void staleLastFlushDoesNotEscalateWhenPendingWorkJustStarted() throws Exception {
    setLongField("lastFlushTime", System.currentTimeMillis() - Duration.ofHours(6).toMillis());

    metrics.recordPageView("health-check", "session-1", "Mozilla/5.0");

    UsageMetricsService.Health health = metrics.getHealth();
    Assertions.assertNotEquals(UsageMetricsService.HealthState.ERROR, health.estado());
  }

  private void setLongField(String fieldName, long value) throws Exception {
    Field f = UsageMetricsService.class.getDeclaredField(fieldName);
    f.setAccessible(true);
    f.setLong(metrics, value);
  }
}
