package com.scanales.eventflow.community;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RegisterForReflection
public record CommunityLightningStateSnapshot(
    int schemaVersion,
    Map<String, CommunityLightningThread> threads,
    Map<String, CommunityLightningComment> comments,
    Map<String, String> threadLikesByUser,
    Map<String, String> commentLikesByUser,
    Map<String, Instant> threadEditedAtById,
    Map<String, Instant> commentEditedAtById,
    Map<String, CommunityLightningReport> reports,
    Map<String, String> reportIndexByUserTarget) {

  public static final int CURRENT_SCHEMA_VERSION = 1;

  public static CommunityLightningStateSnapshot empty() {
    return new CommunityLightningStateSnapshot(
        CURRENT_SCHEMA_VERSION,
        new LinkedHashMap<>(),
        new LinkedHashMap<>(),
        new LinkedHashMap<>(),
        new LinkedHashMap<>(),
        new LinkedHashMap<>(),
        new LinkedHashMap<>(),
        new LinkedHashMap<>(),
        new LinkedHashMap<>());
  }
}
