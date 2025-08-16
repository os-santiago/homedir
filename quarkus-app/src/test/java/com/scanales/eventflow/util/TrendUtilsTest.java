package com.scanales.eventflow.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TrendUtilsTest {

  @Test
  public void testPositiveTrend() {
    TrendUtils.Trend t = TrendUtils.calculate(100, 80, 20, 1);
    Assertions.assertEquals("\u25B2 +25%", t.badge());
  }

  @Test
  public void testLowSample() {
    TrendUtils.Trend t = TrendUtils.calculate(30, 10, 20, 1);
    Assertions.assertEquals("muestra baja", t.badge());
  }

  @Test
  public void testNew() {
    TrendUtils.Trend t = TrendUtils.calculate(12, 0, 20, 1);
    Assertions.assertEquals("nuevo +12", t.badge());
  }

  @Test
  public void testFullDrop() {
    TrendUtils.Trend t = TrendUtils.calculate(0, 50, 20, 1);
    Assertions.assertEquals("\u25BC 100%", t.badge());
  }
}
