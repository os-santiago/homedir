package com.scanales.eventflow.community;

import java.net.IDN;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class CommunityUrlNormalizer {
  private static final Set<String> TRACKING_QUERY_KEYS =
      Set.of(
          "utm_source",
          "utm_medium",
          "utm_campaign",
          "utm_term",
          "utm_content",
          "utm_id",
          "gclid",
          "fbclid",
          "ref",
          "ref_src",
          "mc_cid",
          "mc_eid");

  private CommunityUrlNormalizer() {}

  static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(raw.trim()).normalize();
      String scheme = normalizeScheme(uri.getScheme());
      if (scheme == null) {
        return null;
      }

      String host = normalizeHost(uri.getHost());
      if (host == null) {
        return null;
      }

      int port = normalizePort(scheme, uri.getPort());
      String path = normalizePath(uri.getPath());
      String query = normalizeQuery(uri.getRawQuery());

      URI normalized =
          new URI(
              scheme,
              null,
              host,
              port,
              path,
              query != null && !query.isBlank() ? query : null,
              null);
      return normalized.toString();
    } catch (Exception e) {
      return null;
    }
  }

  private static String normalizeScheme(String scheme) {
    if (scheme == null || scheme.isBlank()) {
      return null;
    }
    String normalized = scheme.toLowerCase(Locale.ROOT);
    if (!"http".equals(normalized) && !"https".equals(normalized)) {
      return null;
    }
    return normalized;
  }

  private static String normalizeHost(String host) {
    if (host == null || host.isBlank()) {
      return null;
    }
    try {
      return IDN.toASCII(host.trim()).toLowerCase(Locale.ROOT);
    } catch (Exception e) {
      return null;
    }
  }

  private static int normalizePort(String scheme, int port) {
    if (port <= 0) {
      return -1;
    }
    if ("http".equals(scheme) && port == 80) {
      return -1;
    }
    if ("https".equals(scheme) && port == 443) {
      return -1;
    }
    return port;
  }

  private static String normalizePath(String path) {
    if (path == null || path.isBlank()) {
      return "/";
    }
    String normalized = path;
    while (normalized.length() > 1 && normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized.isBlank() ? "/" : normalized;
  }

  private static String normalizeQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      return null;
    }
    String[] parts = rawQuery.split("&");
    if (parts.length == 0) {
      return null;
    }
    Set<String> unique = new HashSet<>();
    List<String> kept = new ArrayList<>();
    for (String part : parts) {
      if (part == null || part.isBlank()) {
        continue;
      }
      String key = part;
      int eq = part.indexOf('=');
      if (eq >= 0) {
        key = part.substring(0, eq);
      }
      String normalizedKey = key.toLowerCase(Locale.ROOT);
      if (TRACKING_QUERY_KEYS.contains(normalizedKey)) {
        continue;
      }
      if (unique.add(part)) {
        kept.add(part);
      }
    }
    if (kept.isEmpty()) {
      return null;
    }
    kept.sort(Comparator.naturalOrder());
    return String.join("&", kept);
  }
}
