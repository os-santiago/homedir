package com.scanales.eventflow.util;

import com.scanales.eventflow.service.UserProfileService;
import io.quarkus.arc.Arc;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.Locale;
import java.util.Set;

public final class TemplateLocaleUtil {

  private static final Set<String> SUPPORTED_LANGS = Set.of("en", "es");
  private static final String DEFAULT_LANG = "en";

  private TemplateLocaleUtil() {}

  public static TemplateInstance apply(TemplateInstance templateInstance, String localeCode) {
    String normalized = normalize(localeCode);
    Locale locale = Locale.forLanguageTag(normalized);
    return templateInstance
        .setLocale(locale)
        .data("resolvedLocaleCode", normalized)
        .data("locale", locale);
  }

  public static TemplateInstance apply(
      TemplateInstance templateInstance, String localeCode, HttpHeaders headers) {
    String normalized = normalizeOrNull(localeCode);
    if (normalized == null) {
      normalized = normalizeOrNull(resolveProfileLocale());
    }
    if (normalized == null) {
      normalized = normalizeFromHeaders(headers);
    }
    if (normalized == null) {
      normalized = DEFAULT_LANG;
    }
    Locale locale = Locale.forLanguageTag(normalized);
    return templateInstance
        .setLocale(locale)
        .data("resolvedLocaleCode", normalized)
        .data("locale", locale);
  }

  public static String normalize(String localeCode) {
    String normalized = normalizeOrNull(localeCode);
    return normalized != null ? normalized : DEFAULT_LANG;
  }

  private static String normalizeOrNull(String localeCode) {
    if (localeCode == null || localeCode.isBlank()) {
      return null;
    }
    String normalized = localeCode.trim().toLowerCase(Locale.ROOT);
    if (normalized.contains("-")) {
      normalized = normalized.substring(0, normalized.indexOf('-'));
    }
    return SUPPORTED_LANGS.contains(normalized) ? normalized : null;
  }

  private static String normalizeFromHeaders(HttpHeaders headers) {
    if (headers == null) {
      return null;
    }
    for (Locale locale : headers.getAcceptableLanguages()) {
      if (locale == null) {
        continue;
      }
      String normalized = normalizeOrNull(locale.getLanguage());
      if (normalized != null) {
        return normalized;
      }
    }
    return null;
  }

  private static String resolveProfileLocale() {
    try {
      SecurityIdentity identity = Arc.container().instance(SecurityIdentity.class).get();
      if (identity == null || identity.isAnonymous()) {
        return null;
      }
      String userId = AdminUtils.getClaim(identity, "email");
      if (userId == null || userId.isBlank()) {
        userId = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
      }
      if (userId == null || userId.isBlank()) {
        return null;
      }
      UserProfileService userProfiles = Arc.container().instance(UserProfileService.class).get();
      if (userProfiles == null) {
        return null;
      }
      return userProfiles.find(userId.toLowerCase(Locale.ROOT))
          .map(com.scanales.eventflow.model.UserProfile::getPreferredLocale)
          .orElse(null);
    } catch (Exception ignored) {
      return null;
    }
  }
}
