package com.scanales.eventflow.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class PaginationGuardrailsTest {

  @Test
  void clampLimitUsesDefaultAndMax() {
    assertEquals(20, PaginationGuardrails.clampLimit((Integer) null, 20, 100));
    assertEquals(20, PaginationGuardrails.clampLimit(0, 20, 100));
    assertEquals(35, PaginationGuardrails.clampLimit(35, 20, 100));
    assertEquals(100, PaginationGuardrails.clampLimit(999, 20, 100));
  }

  @Test
  void clampOffsetCapsAtConfiguredMaximum() {
    assertEquals(0, PaginationGuardrails.clampOffset((Integer) null, 5000));
    assertEquals(0, PaginationGuardrails.clampOffset(-10, 5000));
    assertEquals(450, PaginationGuardrails.clampOffset(450, 5000));
    assertEquals(5000, PaginationGuardrails.clampOffset(9000, 5000));
  }

  @Test
  void clampWindowStepRoundsAndCaps() {
    assertEquals(10, PaginationGuardrails.clampWindowStep(null, 10, 10, 100));
    assertEquals(10, PaginationGuardrails.clampWindowStep(1, 10, 10, 100));
    assertEquals(20, PaginationGuardrails.clampWindowStep(11, 10, 10, 100));
    assertEquals(100, PaginationGuardrails.clampWindowStep(500, 10, 10, 100));
  }
}
