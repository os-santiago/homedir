package com.scanales.homedir.util;

import io.quarkus.arc.Arc;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.Locale;

public final class TemplateLocaleUtil {

  private TemplateLocaleUtil() {}

  public static TemplateInstance apply(TemplateInstance templateInstance, String localeCode) {
    return apply(templateInstance, localeCode, resolveCurrentHeaders());
  }

  public static TemplateInstance apply(
      TemplateInstance templateInstance, String localeCode, HttpHeaders headers) {
    String normalized = LocaleResolver.resolveFromCookie(localeCode, headers);
    Locale locale = Locale.forLanguageTag(normalized);
    return templateInstance
        .setLocale(locale)
        .data("resolvedLocaleCode", normalized)
        .data("locale", locale);
  }

  public static String resolve(String localeCode, HttpHeaders headers) {
    return LocaleResolver.resolveFromCookie(localeCode, headers);
  }

  public static String normalize(String localeCode) {
    String normalized = LocaleResolver.normalizeOrNull(localeCode);
    return normalized != null ? normalized : LocaleResolver.DEFAULT_LANG;
  }

  private static HttpHeaders resolveCurrentHeaders() {
    try {
      return Arc.container().instance(HttpHeaders.class).get();
    } catch (Exception ignored) {
      return null;
    }
  }
}
