package com.scanales.eventflow.cfp;

import java.util.Locale;
import java.util.Optional;

public enum CfpPanelistStatus {
  LINKED("linked"),
  PENDING_LOGIN("pending_login");

  private final String apiValue;

  CfpPanelistStatus(String apiValue) {
    this.apiValue = apiValue;
  }

  public String apiValue() {
    return apiValue;
  }

  public static Optional<CfpPanelistStatus> fromApi(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (CfpPanelistStatus value : values()) {
      if (value.apiValue.equals(normalized)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}

