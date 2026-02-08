package com.scanales.eventflow.community;

import java.util.Locale;
import java.util.Optional;

public enum CommunitySubmissionStatus {
  PENDING("pending"),
  APPROVED("approved"),
  REJECTED("rejected");

  private final String apiValue;

  CommunitySubmissionStatus(String apiValue) {
    this.apiValue = apiValue;
  }

  public String apiValue() {
    return apiValue;
  }

  public static Optional<CommunitySubmissionStatus> fromApi(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "pending" -> Optional.of(PENDING);
      case "approved" -> Optional.of(APPROVED);
      case "rejected" -> Optional.of(REJECTED);
      default -> Optional.empty();
    };
  }
}
