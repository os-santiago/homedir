package com.scanales.homedir.volunteers;

import java.util.Locale;
import java.util.Optional;

public enum VolunteerLoungeMessageType {
  POST("post"),
  ANNOUNCEMENT("announcement");

  private final String apiValue;

  VolunteerLoungeMessageType(String apiValue) {
    this.apiValue = apiValue;
  }

  public String apiValue() {
    return apiValue;
  }

  public static Optional<VolunteerLoungeMessageType> fromApi(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (VolunteerLoungeMessageType value : values()) {
      if (value.apiValue.equals(normalized)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
