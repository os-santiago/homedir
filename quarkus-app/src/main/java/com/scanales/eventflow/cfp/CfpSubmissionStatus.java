package com.scanales.eventflow.cfp;

import java.util.Locale;
import java.util.Optional;

public enum CfpSubmissionStatus {
  PENDING("pending"),
  UNDER_REVIEW("under_review"),
  ACCEPTED("accepted"),
  REJECTED("rejected"),
  WITHDRAWN("withdrawn");

  private final String apiValue;

  CfpSubmissionStatus(String apiValue) {
    this.apiValue = apiValue;
  }

  public String apiValue() {
    return apiValue;
  }

  public static Optional<CfpSubmissionStatus> fromApi(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "pending" -> Optional.of(PENDING);
      case "under_review", "under-review" -> Optional.of(UNDER_REVIEW);
      case "accepted" -> Optional.of(ACCEPTED);
      case "rejected" -> Optional.of(REJECTED);
      case "withdrawn" -> Optional.of(WITHDRAWN);
      default -> Optional.empty();
    };
  }
}
