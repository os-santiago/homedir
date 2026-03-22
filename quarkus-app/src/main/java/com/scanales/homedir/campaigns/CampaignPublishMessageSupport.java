package com.scanales.homedir.campaigns;

final class CampaignPublishMessageSupport {
  private static final String DEFAULT_BASE_URL = "https://homedir.opensourcesantiago.io";
  private static final String DEFAULT_CAMPAIGN = "homedir-campaigns";

  private CampaignPublishMessageSupport() {}

  static String messageFor(CampaignDraftState draft, String channel) {
    return "**HomeDir Campaign**\n"
        + editorialTitle(draft)
        + "\n"
        + "Track the latest verified progress inside HomeDir.\n"
        + trackedUrl(draft, channel);
  }

  static String dedupeSignature(CampaignDraftState draft, String channel) {
    String path = safe(draft.metadata().get("eventUrl"));
    if (path.isBlank()) {
      path = "/about";
    }
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    return sanitizeForSignature(channel)
        + "|"
        + sanitizeForSignature(editorialTitle(draft))
        + "|"
        + sanitizeForSignature(path)
        + "|track-the-latest-verified-progress-inside-homedir";
  }

  static String trackedUrl(CampaignDraftState draft, String channel) {
    String path = safe(draft.metadata().get("eventUrl"));
    if (path.isBlank()) {
      path = "/about";
    }
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    return DEFAULT_BASE_URL
        + path
        + (path.contains("?") ? "&" : "?")
        + "utm_source=campaigns&utm_medium="
        + sanitizeToken(channel, "internal")
        + "&utm_campaign="
        + DEFAULT_CAMPAIGN
        + "&utm_content="
        + sanitizeToken(draft.id(), "draft");
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

  private static String editorialTitle(CampaignDraftState draft) {
    String title = safe(draft.metadata().get("eventTitle"));
    if (title.isBlank()) {
      title = safe(draft.metadata().get("title"));
    }
    if (title.isBlank()) {
      title = safe(draft.id());
    }
    return title;
  }

  private static String sanitizeForSignature(String value) {
    return safe(value).toLowerCase().replaceAll("\\s+", " ");
  }

  private static String sanitizeToken(String value, String fallback) {
    String normalized = safe(value).toLowerCase();
    if (normalized.isBlank()) {
      return fallback;
    }
    StringBuilder safe = new StringBuilder(normalized.length());
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if ((c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_') {
        safe.append(c);
      }
    }
    return safe.isEmpty() ? fallback : safe.toString();
  }
}
