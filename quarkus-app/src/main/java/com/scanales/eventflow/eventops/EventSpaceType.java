package com.scanales.eventflow.eventops;

import java.util.Locale;
import java.util.Optional;

public enum EventSpaceType {
  MAIN_STAGE("main_stage"),
  SECONDARY_STAGE("secondary_stage"),
  ROOM("room"),
  FOYER("foyer"),
  FOOD("food"),
  VIP("vip"),
  NETWORKING("networking"),
  ENTRY("entry"),
  OTHER("other");

  private final String apiValue;

  EventSpaceType(String apiValue) {
    this.apiValue = apiValue;
  }

  public String apiValue() {
    return apiValue;
  }

  public static Optional<EventSpaceType> fromApi(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (EventSpaceType value : values()) {
      if (value.apiValue.equals(normalized)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
