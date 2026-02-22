package com.scanales.eventflow.community;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;

@RegisterForReflection
public record CommunityLightningComment(
    String id,
    String threadId,
    String body,
    String userId,
    String userName,
    Instant createdAt,
    Instant updatedAt,
    int likes,
    int reports) {}
