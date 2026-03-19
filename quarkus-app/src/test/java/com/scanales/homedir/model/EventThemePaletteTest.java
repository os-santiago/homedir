package com.scanales.homedir.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class EventThemePaletteTest {

  @Test
  void resolvesDevOpsDaysPresetWhenNoCustomPaletteExists() {
    Event event = new Event();
    event.setId("devopsdays-santiago-2026");

    assertEquals("#e91c26", event.getResolvedThemePrimaryColor());
    assertEquals("#50bce7", event.getResolvedThemeAccentColor());
    assertEquals("#082440", event.getResolvedThemeSurfaceColor());
    assertEquals("#ffffff", event.getResolvedThemeTextColor());
  }

  @Test
  void customPaletteOverridesPresetValues() {
    Event event = new Event();
    event.setId("devopsdays-santiago-2026");
    event.setThemePrimaryColor("#123abc");
    event.setThemeAccentColor("#5f5f5f");

    assertEquals("#123abc", event.getResolvedThemePrimaryColor());
    assertEquals("#5f5f5f", event.getResolvedThemeAccentColor());
    assertEquals("#082440", event.getResolvedThemeSurfaceColor());
  }

  @Test
  void normalizesShortHexAndIgnoresInvalidValues() {
    Event event = new Event();
    event.setThemePrimaryColor("#abc");
    event.setThemeAccentColor("not-a-color");

    assertEquals("#aabbcc", event.getThemePrimaryColor());
    assertNull(event.getThemeAccentColor());
    assertEquals("#72ff9f", event.getResolvedThemeAccentColor());
  }
}
