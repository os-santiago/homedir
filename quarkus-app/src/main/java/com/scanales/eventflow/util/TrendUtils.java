package com.scanales.eventflow.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Utility methods to compute and format metric trends. */
public final class TrendUtils {
  private static final DecimalFormatSymbols US_SYMBOLS =
      DecimalFormatSymbols.getInstance(Locale.US);

  private TrendUtils() {}

  /** Result of a trend calculation. */
  public record Trend(String badge, String ariaLabel) {}

  /**
   * Calculates the trend between current and base values following product rules.
   *
   * @param current value for the selected range
   * @param base value for the previous equivalent range
   * @param minBaseline minimum base value to display percentages
   * @param decimals decimals when |Δ|<10%
   * @return trend with badge text and aria label
   */
  public static Trend calculate(long current, long base, int minBaseline, int decimals) {
    if (base == 0 && current > 0) {
      return new Trend("nuevo +" + current, "Nuevo respecto al período anterior");
    }
    if (base < minBaseline) {
      return new Trend("muestra baja", "Muestra baja respecto al período anterior");
    }
    if (current == 0) {
      if (base >= minBaseline) {
        return new Trend("\u25BC 100%", "Bajó 100% respecto al período anterior");
      }
      return new Trend("muestra baja", "Muestra baja respecto al período anterior");
    }
    double pct = (double) (current - base) / base * 100d;
    String sign = pct >= 0 ? "\u25B2" : "\u25BC"; // ▲ ▼
    double absPct = Math.abs(pct);
    String formatted;
    String prefix = pct >= 0 ? "+" : "-";
    if (absPct < 0.1d) {
      formatted = "<0.1%";
      prefix = ""; // no sign for very small changes
    } else if (absPct < 10d) {
      DecimalFormat df = new DecimalFormat("#0." + "0".repeat(decimals), US_SYMBOLS);
      formatted = df.format(absPct) + "%";
    } else {
      formatted = Math.round(absPct) + "%";
    }
    String aria = (pct >= 0 ? "Subió " : "Bajó ") + formatted + " respecto al período anterior";
    return new Trend(sign + " " + prefix + formatted, aria);
  }
}
