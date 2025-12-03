package com.scanales.eventflow.public_.landing;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record LandingViewModel(
    LandingCharacterStats character,
    LandingCommunityStats community
) {}
