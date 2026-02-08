package com.scanales.eventflow.community;

import java.util.Locale;
import java.util.Optional;

public enum CommunityBoardGroup {
  HOMEDIR_USERS("homedir-users"),
  GITHUB_USERS("github-users"),
  DISCORD_USERS("discord-users");

  private final String path;

  CommunityBoardGroup(String path) {
    this.path = path;
  }

  public String path() {
    return path;
  }

  public static Optional<CommunityBoardGroup> fromPath(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (CommunityBoardGroup value : values()) {
      if (value.path.equals(normalized)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
