package com.scanales.eventflow.community;

import java.util.Locale;
import java.util.Optional;

public enum CommunityVoteType {
  RECOMMENDED("recommended"),
  MUST_SEE("must_see"),
  NOT_FOR_ME("not_for_me");

  private final String apiValue;

  CommunityVoteType(String apiValue) {
    this.apiValue = apiValue;
  }

  public String apiValue() {
    return apiValue;
  }

  public static Optional<CommunityVoteType> fromApi(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "recommended" -> Optional.of(RECOMMENDED);
      case "must_see" -> Optional.of(MUST_SEE);
      case "not_for_me" -> Optional.of(NOT_FOR_ME);
      default -> Optional.empty();
    };
  }
}

