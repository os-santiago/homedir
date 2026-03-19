package com.scanales.homedir.campaigns;

final class CampaignPublishMessageSupport {
  private CampaignPublishMessageSupport() {}

  static String messageFor(CampaignDraftState draft) {
    String title = safe(draft.metadata().get("eventTitle"));
    if (title.isBlank()) {
      title = safe(draft.metadata().get("title"));
    }
    if (title.isBlank()) {
      title = safe(draft.id());
    }
    String ctaUrl = safe(draft.metadata().get("eventUrl"));
    if (ctaUrl.isBlank()) {
      ctaUrl = "/about";
    }
    return "**HomeDir Campaign**\n"
        + title
        + "\n"
        + "Track the latest verified progress inside HomeDir.\n"
        + "https://homedir.opensourcesantiago.io"
        + ctaUrl;
  }

  static String safe(String raw) {
    return raw == null ? "" : raw.trim();
  }

  static String escapeJson(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "");
  }

  static String truncate(String value, int limit) {
    if (value == null || value.length() <= limit) {
      return value == null ? "" : value;
    }
    return value.substring(0, Math.max(0, limit - 1)) + "…";
  }

  static String normalizeBaseUrl(String value, String fallback) {
    String normalized = safe(value);
    if (normalized.isBlank()) {
      normalized = fallback;
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
