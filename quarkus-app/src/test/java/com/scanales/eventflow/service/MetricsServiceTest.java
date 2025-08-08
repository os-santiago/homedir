package com.scanales.eventflow.service;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MetricsServiceTest {

    @Inject
    MetricsService metricsService;

    @Test
    public void metricsWithinBounds() {
        MetricsService.Metrics m = metricsService.getMetrics();
        Assertions.assertTrue(m.cpu() >= 0 && m.cpu() <= 100);
        Assertions.assertTrue(m.memory() >= 0 && m.memory() <= 100);
        Assertions.assertTrue(m.disk() >= 0 && m.disk() <= 100);
    }
}
