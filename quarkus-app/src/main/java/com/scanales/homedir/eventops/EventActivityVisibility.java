package com.scanales.homedir.eventops;

import java.util.Locale;
import java.util.Optional;

public enum EventActivityVisibility {
  PUBLIC("public"),
  STAFF("staff");

  private final String apiValue;

  EventActivityVisibility(String apiValue) {
    this.apiValue = apiValue;
  }

  public String apiValue() {
    return apiValue;
  }

  public static Optional<EventActivityVisibility> fromApi(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (EventActivityVisibility value : values()) {
      if (value.apiValue.equals(normalized)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
