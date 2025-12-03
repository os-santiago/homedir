package com.scanales.eventflow.public_.landing;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record LandingCommunityStats(
    int totalMembers,
    int totalXp,
    int totalQuests,
    int totalProjects
) {}
