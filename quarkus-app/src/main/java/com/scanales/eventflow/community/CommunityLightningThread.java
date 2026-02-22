package com.scanales.eventflow.community;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;

@RegisterForReflection
public record CommunityLightningThread(
    String id,
    String mode,
    String title,
    String body,
    String userId,
    String userName,
    Instant createdAt,
    Instant updatedAt,
    Instant publishedAt,
    String bestCommentId,
    int likes,
    int comments,
    int reports) {}
