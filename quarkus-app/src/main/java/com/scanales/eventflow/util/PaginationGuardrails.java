package com.scanales.eventflow.util;

/**
 * Shared guardrails for paginated queries and history windows.
 *
 * <p>This keeps list retrieval predictable and bounded to avoid oversized payloads and accidental
 * memory pressure from unbounded search/history scans.
 */
public final class PaginationGuardrails {
  public static final int DEFAULT_PAGE_LIMIT = 20;
  public static final int MAX_PAGE_LIMIT = 100;
  public static final int MAX_OFFSET = 5_000;
  public static final int DEFAULT_HISTORY_STEP = 10;
  public static final int MAX_HISTORY_WINDOW = 100;

  private PaginationGuardrails() {}

  public static int clampLimit(Integer requested, int defaultLimit, int maxLimit) {
    return clampLimit(requested == null ? defaultLimit : requested, defaultLimit, maxLimit);
  }

  public static int clampLimit(int requested, int defaultLimit, int maxLimit) {
    int safeDefault = Math.max(1, Math.min(defaultLimit, Math.max(1, maxLimit)));
    if (requested <= 0) {
      return safeDefault;
    }
    return Math.min(requested, Math.max(1, maxLimit));
  }

  public static int clampOffset(Integer requested, int maxOffset) {
    return clampOffset(requested == null ? 0 : requested, maxOffset);
  }

  public static int clampOffset(int requested, int maxOffset) {
    int safeMax = Math.max(0, maxOffset);
    if (requested <= 0) {
      return 0;
    }
    return Math.min(requested, safeMax);
  }

  /**
   * Clamp a history retrieval window to fixed steps (e.g. 10, 20, 30...) with a max cap.
   */
  public static int clampWindowStep(
      Integer requestedWindow, int step, int defaultWindow, int maxWindow) {
    int safeStep = Math.max(1, step);
    int safeDefault = Math.max(safeStep, defaultWindow);
    int safeMax = Math.max(safeDefault, maxWindow);
    int raw = requestedWindow == null ? safeDefault : requestedWindow;
    if (raw <= 0) {
      raw = safeDefault;
    }
    int normalized = ((raw + safeStep - 1) / safeStep) * safeStep;
    return Math.min(Math.max(safeStep, normalized), safeMax);
  }

  public static int nextWindow(int current, int step, int maxWindow) {
    int safeStep = Math.max(1, step);
    int safeCurrent = Math.max(safeStep, current);
    return Math.min(safeCurrent + safeStep, Math.max(safeCurrent, maxWindow));
  }
}
