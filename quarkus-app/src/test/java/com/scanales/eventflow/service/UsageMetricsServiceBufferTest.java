package com.scanales.eventflow.service;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class UsageMetricsServiceBufferTest {

    @Inject
    UsageMetricsService metrics;

    @BeforeEach
    void reset() {
        metrics.reset();
    }

    @Test
    public void bufferFullIsCountedAndDiscarded() {
        metrics.recordPageView("route1", "s1", "Mozilla/5.0");
        metrics.recordPageView("route2", "s2", "Mozilla/5.0");
        metrics.recordPageView("route3", "s3", "Mozilla/5.0");
        Map<String, Long> snap = metrics.snapshot();
        Assertions.assertNotNull(snap.get("page_view:route1"));
        Assertions.assertNotNull(snap.get("page_view:route2"));
        Assertions.assertNull(snap.get("page_view:route3"));
        Map<String, Long> disc = metrics.getDiscarded();
        Assertions.assertEquals(1L, disc.getOrDefault("buffer_full", 0L));
    }
}
