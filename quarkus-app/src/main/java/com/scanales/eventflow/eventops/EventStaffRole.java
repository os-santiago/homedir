package com.scanales.eventflow.eventops;

import java.util.Locale;
import java.util.Optional;

public enum EventStaffRole {
  ORGANIZER("organizer"),
  VOLUNTEER("volunteer"),
  PRODUCTION("production"),
  OPERATIONS("operations");

  private final String apiValue;

  EventStaffRole(String apiValue) {
    this.apiValue = apiValue;
  }

  public String apiValue() {
    return apiValue;
  }

  public static Optional<EventStaffRole> fromApi(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (EventStaffRole value : values()) {
      if (value.apiValue.equals(normalized)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
