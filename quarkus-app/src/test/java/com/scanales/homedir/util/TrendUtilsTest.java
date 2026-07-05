package com.scanales.homedir.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TrendUtilsTest {

  @Test
  public void testPositiveTrend() {
    TrendUtils.Trend t = TrendUtils.calculate(100, 80, 20, 1);
    Assertions.assertEquals("\u25B2 +25%", t.badge());
    Assertions.assertEquals("Subi\u00f3 25% respecto al per\u00edodo anterior", t.ariaLabel());
  }

  @Test
  public void testLowSample() {
    TrendUtils.Trend t = TrendUtils.calculate(30, 10, 20, 1);
    Assertions.assertEquals("muestra baja", t.badge());
    Assertions.assertEquals("Muestra baja respecto al per\u00edodo anterior", t.ariaLabel());
  }

  @Test
  public void testNew() {
    TrendUtils.Trend t = TrendUtils.calculate(12, 0, 20, 1);
    Assertions.assertEquals("nuevo +12", t.badge());
    Assertions.assertEquals("Nuevo respecto al per\u00edodo anterior", t.ariaLabel());
  }

  @Test
  public void testFullDrop() {
    TrendUtils.Trend t = TrendUtils.calculate(0, 50, 20, 1);
    Assertions.assertEquals("\u25BC 100%", t.badge());
    Assertions.assertEquals("Baj\u00f3 100% respecto al per\u00edodo anterior", t.ariaLabel());
  }

  @Test
  public void testNegativeTrend() {
    TrendUtils.Trend t = TrendUtils.calculate(60, 100, 20, 1);
    Assertions.assertEquals("\u25BC -40%", t.badge());
    Assertions.assertEquals("Baj\u00f3 40% respecto al per\u00edodo anterior", t.ariaLabel());
  }

  @Test
  public void testZeroChangeIsLessThanZeroPointOnePercent() {
    TrendUtils.Trend t = TrendUtils.calculate(50, 50, 20, 1);
    Assertions.assertEquals("\u25B2 <0.1%", t.badge());
    Assertions.assertEquals("Subi\u00f3 <0.1% respecto al per\u00edodo anterior", t.ariaLabel());
  }

  @Test
  public void testVerySmallPositiveChangeUsesDecimals() {
    TrendUtils.Trend t = TrendUtils.calculate(1003, 1000, 20, 1);
    Assertions.assertEquals("\u25B2 +0.3%", t.badge());
    Assertions.assertEquals("Subi\u00f3 0.3% respecto al per\u00edodo anterior", t.ariaLabel());
  }

  @Test
  public void testVerySmallNegativeChangeUsesDecimals() {
    TrendUtils.Trend t = TrendUtils.calculate(997, 1000, 20, 1);
    Assertions.assertEquals("\u25BC -0.3%", t.badge());
    Assertions.assertEquals("Baj\u00f3 0.3% respecto al per\u00edodo anterior", t.ariaLabel());
  }

  @Test
  public void testLargeBaseVerySmallChangeIsLessThanZeroPointOnePercent() {
    TrendUtils.Trend t = TrendUtils.calculate(10001, 10000, 20, 1);
    Assertions.assertEquals("\u25B2 <0.1%", t.badge());
    Assertions.assertEquals("Subi\u00f3 <0.1% respecto al per\u00edodo anterior", t.ariaLabel());
  }

  @Test
  public void testLargeBaseVerySmallNegativeChangeIsLessThanZeroPointOnePercent() {
    TrendUtils.Trend t = TrendUtils.calculate(9999, 10000, 20, 1);
    Assertions.assertEquals("\u25BC <0.1%", t.badge());
    Assertions.assertEquals("Baj\u00f3 <0.1% respecto al per\u00edodo anterior", t.ariaLabel());
  }

  @Test
  public void testSmallChangeWithDecimalsRespectsDecimalParam() {
    TrendUtils.Trend t = TrendUtils.calculate(103, 100, 20, 2);
    Assertions.assertEquals("\u25B2 +3.00%", t.badge());
    Assertions.assertEquals("Subi\u00f3 3.00% respecto al per\u00edodo anterior", t.ariaLabel());
  }

  @Test
  public void testExactTenPercentRounded() {
    TrendUtils.Trend t = TrendUtils.calculate(110, 100, 20, 1);
    Assertions.assertEquals("\u25B2 +10%", t.badge());
    Assertions.assertEquals("Subi\u00f3 10% respecto al per\u00edodo anterior", t.ariaLabel());
  }

  @Test
  public void testCurrentZeroButBaseLowSample() {
    TrendUtils.Trend t = TrendUtils.calculate(0, 5, 20, 1);
    Assertions.assertEquals("muestra baja", t.badge());
    Assertions.assertEquals("Muestra baja respecto al per\u00edodo anterior", t.ariaLabel());
  }
}
