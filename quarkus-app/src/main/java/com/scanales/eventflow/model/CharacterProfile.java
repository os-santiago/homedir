package com.scanales.eventflow.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CharacterProfile(
        String title,
        int level,
        int xp,
        int maxXp, // Level cap or next level threshold
        int hp,
        int maxHp,
        int sp,
        int maxSp,
        int contribCount,
        int questsCount,
        int eventsCount,
        int projectsCount,
        int networkCount) {
    public static CharacterProfile visitor() {
        return new CharacterProfile("VISITOR", 1, 0, 100, 10, 100, 5, 100, 0, 0, 0, 0, 0);
    }

    public static CharacterProfile novice() {
        return new CharacterProfile("NOVICE", 1, 0, 100, 50, 100, 20, 100, 0, 0, 0, 0, 0);
    }
}
