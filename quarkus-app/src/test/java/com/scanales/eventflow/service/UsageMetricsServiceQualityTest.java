package com.scanales.eventflow.service;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class UsageMetricsServiceQualityTest {

    @Inject
    UsageMetricsService metrics;

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
}
