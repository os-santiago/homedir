package com.scanales.eventflow.security;

import java.net.URI;

/** Utility methods to keep post-login redirects inside the application. */
public final class RedirectSanitizer {

  private RedirectSanitizer() {}

  public static String sanitizeInternalRedirect(String redirect, String fallback) {
    String safeFallback = normalizeFallback(fallback);
    if (redirect == null) {
      return safeFallback;
    }

    String candidate = redirect.trim();
    if (candidate.isEmpty()) {
      return safeFallback;
    }
    if (candidate.indexOf('\r') >= 0 || candidate.indexOf('\n') >= 0) {
      return safeFallback;
    }
    if (!candidate.startsWith("/") || candidate.startsWith("//")) {
      return safeFallback;
    }

    try {
      URI uri = URI.create(candidate);
      if (uri.isAbsolute()) {
        return safeFallback;
      }
      return candidate;
    } catch (IllegalArgumentException ignored) {
      return safeFallback;
    }
  }

  private static String normalizeFallback(String fallback) {
    if (fallback == null || fallback.isBlank()) {
      return "/";
    }
    String candidate = fallback.trim();
    if (!candidate.startsWith("/") || candidate.startsWith("//")) {
      return "/";
    }
    return candidate;
  }
}
