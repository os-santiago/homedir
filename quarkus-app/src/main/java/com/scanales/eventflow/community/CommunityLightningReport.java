package com.scanales.eventflow.community;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;

@RegisterForReflection
public record CommunityLightningReport(
    String id,
    String targetType,
    String targetId,
    String threadId,
    String userId,
    String userName,
    String reason,
    Instant createdAt) {}
