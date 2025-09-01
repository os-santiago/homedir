package com.scanales.eventflow.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class UsageMetricsServiceQualityTest {

  @Inject UsageMetricsService metrics;

  @BeforeEach
  void reset() {
    metrics.reset();
  }

  @Test
  public void talkViewDedupesAndBotsDiscarded() {
    metrics.recordTalkView("t1", "s1", "Mozilla/5.0");
    metrics.recordTalkView("t1", "s1", "Mozilla/5.0");
    metrics.recordTalkView("t2", "s2", "Googlebot/2.1");
    Map<String, Long> snap = metrics.snapshot();
    Assertions.assertEquals(1L, snap.getOrDefault("talk_view:t1", 0L));
    Assertions.assertNull(snap.get("talk_view:t2"));
    Map<String, Long> discards = metrics.getDiscarded();
    Assertions.assertEquals(1L, discards.getOrDefault("dedupe", 0L));
    Assertions.assertEquals(1L, discards.getOrDefault("bot", 0L));
  }

  @Test
  public void talkRegisterAndUnregisterUpdatesMetrics() {
    metrics.recordTalkRegister("t9", List.of(), "Mozilla/5.0", "User", "u@example.com");
    Map<String, Long> snap = metrics.snapshot();
    Assertions.assertEquals(1L, snap.getOrDefault("talk_register:t9", 0L));
    Assertions.assertEquals(1, metrics.getRegistrants("t9").size());

    metrics.recordTalkUnregister("t9", "u@example.com");
    snap = metrics.snapshot();
    Assertions.assertNull(snap.get("talk_register:t9"));
    Assertions.assertTrue(metrics.getRegistrants("t9").isEmpty());
  }
}
