package com.scanales.eventflow.public_.landing;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LandingService {

  @Inject SecurityIdentity identity;

  // TODO: inyectar servicios reales si existen (CommunityService, EventService, ProjectService, etc.)

  public LandingViewModel buildViewModel() {
    boolean loggedIn = identity != null && !identity.isAnonymous();

    String displayName = loggedIn ? identity.getPrincipal().getName() : "NOVICE GUEST";
    String roleLabel = loggedIn ? "DEVELOPER" : "VISITOR";

    int level = loggedIn ? 3 : 1;
    int hpPercent = loggedIn ? 80 : 10;
    int spPercent = loggedIn ? 65 : 5;
    int xpCurrent = loggedIn ? 40 : 0;
    int xpMax = loggedIn ? 100 : 100;

    int contributions = 0;
    int quests = 0;
    int events = 0;
    int projects = 0;
    int connections = 0;
    int totalXp = 0;

    LandingCharacterStats character =
        new LandingCharacterStats(
            loggedIn,
            displayName,
            roleLabel,
            level,
            hpPercent,
            spPercent,
            xpCurrent,
            xpMax,
            contributions,
            quests,
            events,
            projects,
            connections,
            totalXp);

    int totalMembers = 0;
    int communityTotalXp = 0;
    int totalQuests = 0;
    int totalProjects = 0;

    LandingCommunityStats community =
        new LandingCommunityStats(totalMembers, communityTotalXp, totalQuests, totalProjects);

    return new LandingViewModel(character, community);
  }
}
