package com.scanales.homedir.volunteers;

import java.util.Locale;
import java.util.Optional;

public enum VolunteerApplicationStatus {
  APPLIED("applied"),
  UNDER_REVIEW("under_review"),
  SELECTED("selected"),
  NOT_SELECTED("not_selected"),
  WITHDRAWN("withdrawn");

  private final String apiValue;

  VolunteerApplicationStatus(String apiValue) {
    this.apiValue = apiValue;
  }

  public String apiValue() {
    return apiValue;
  }

  public static Optional<VolunteerApplicationStatus> fromApi(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "applied", "submitted", "postulada" -> Optional.of(APPLIED);
      case "under_review", "under-review", "en_revision" -> Optional.of(UNDER_REVIEW);
      case "selected", "approved", "aceptada" -> Optional.of(SELECTED);
      case "not_selected", "rejected", "no_seleccionada" -> Optional.of(NOT_SELECTED);
      case "withdrawn", "retirada" -> Optional.of(WITHDRAWN);
      default -> Optional.empty();
    };
  }
}