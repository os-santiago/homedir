package com.scanales.eventflow.public_.landing;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record LandingCharacterStats(
    boolean loggedIn,
    String displayName,
    String roleLabel,
    int level,
    int hpPercent,
    int spPercent,
    int xpCurrent,
    int xpMax,
    int contributions,
    int quests,
    int events,
    int projects,
    int connections,
    int totalXp
) {}
