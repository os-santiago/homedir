package com.scanales.homedir.public_;

import com.scanales.homedir.service.CommunityService;
import com.scanales.homedir.service.UserProfileService;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/landing/stats")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class LandingStatsResource {

  @Inject CommunityService communityService;
  @Inject UserProfileService userProfileService;

  @GET
  public LandingStats getStats() {
    LandingStats stats = new LandingStats();
    Map<String, com.scanales.homedir.model.UserProfile> profiles = userProfileService.allProfiles();

    stats.totalMembers = communityService.countMembers();
    stats.totalXP = profiles.values().stream().mapToLong(p -> Math.max(0, p.getCurrentXp())).sum();
    stats.totalQuests = profiles.values().stream()
        .map(com.scanales.homedir.model.UserProfile::getHistory)
        .filter(Objects::nonNull)
        .mapToLong(java.util.List::size)
        .sum();
    stats.totalProjects = profiles.values().stream()
        .map(com.scanales.homedir.model.UserProfile::getGithub)
        .filter(Objects::nonNull)
        .map(com.scanales.homedir.model.UserProfile.GithubAccount::login)
        .filter(Objects::nonNull)
        .map(login -> login.trim().toLowerCase(Locale.ROOT))
        .filter(login -> !login.isBlank())
        .distinct()
        .count();

    return stats;
  }

  public static class LandingStats {
    public long totalMembers;
    public long totalXP;
    public long totalQuests;
    public long totalProjects;
  }
}
