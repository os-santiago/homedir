package com.scanales.homedir.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class LocaleResolverTest {

  @Test
  void explicitParamWinsOverEverything() {
    assertEquals(
        "en", LocaleResolver.resolve("es", "en", "es", "es", List.of(Locale.forLanguageTag("es"))));
  }

  @Test
  void pathPrefixWinsOverProfileCookieHeader() {
    assertEquals(
        "es", LocaleResolver.resolve("en", null, "es", "en", List.of(Locale.forLanguageTag("en"))));
  }

  @Test
  void profileWinsOverCookieAndHeader() {
    assertEquals(
        "es", LocaleResolver.resolve("es", null, null, "en", List.of(Locale.forLanguageTag("en"))));
  }

  @Test
  void cookieWinsOverHeader() {
    assertEquals(
        "en", LocaleResolver.resolve(null, null, null, "en", List.of(Locale.forLanguageTag("es"))));
  }

  @Test
  void headerUsedWhenNoStrongerSource() {
    assertEquals(
        "es", LocaleResolver.resolve(null, null, null, null, List.of(Locale.forLanguageTag("es"))));
  }

  @Test
  void defaultWhenNothingMatches() {
    assertEquals("en", LocaleResolver.resolve(null, null, null, null, List.of()));
  }

  @Test
  void normalizeOrNullEnforcesSupportedLanguages() {
    assertEquals("en", LocaleResolver.normalizeOrNull("EN"));
    assertEquals("es", LocaleResolver.normalizeOrNull("es-ES"));
    assertNull(LocaleResolver.normalizeOrNull("fr"));
    assertNull(LocaleResolver.normalizeOrNull(""));
  }
}
