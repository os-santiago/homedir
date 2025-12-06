package com.scanales.eventflow.public_;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/me")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class UserProfileResource {

  @Inject
  SecurityIdentity identity;

  public static class UserProfile {
    public boolean authenticated;
    public String userId;
    public String displayName;
    public String avatarUrl;

    // Stats básicos para la ficha (por ahora dummy)
    public int level;
    public long experience;
    public int contributions;
    public int questsCompleted;
    public int eventsAttended;
    public int projectsHosted;
    public int connections;
  }

  @Inject
  com.scanales.eventflow.service.CommunityService communityService;

  @GET
  public Response me() {
    UserProfile profile = new UserProfile();

    if (identity == null || identity.isAnonymous()) {
      profile.authenticated = false;
      profile.userId = null;
      profile.displayName = "Novice Guest";
      profile.avatarUrl = null;

      // Stats mínimos base
      profile.level = 1;
      profile.experience = 0;
      profile.contributions = 0;
      profile.questsCompleted = 0;
      profile.eventsAttended = 0;
      profile.projectsHosted = 0;
      profile.connections = 0;

      return Response.ok(profile).build();
    }

    profile.authenticated = true;
    profile.userId = identity.getPrincipal().getName();
    profile.displayName = identity.getPrincipal().getName(); // Default fallback
    profile.avatarUrl = null;

    // Fetch real member data from CommunityService (os-santiago)
    java.util.Optional<com.scanales.eventflow.model.CommunityMember> memberOpt = communityService
        .findByUserId(profile.userId);

    // Also try by GitHub login if available in profile (via UserProfileService -
    // which we need to inject if we want full link)
    // But for now, CommunityService.findByUserId should work if they are linked.

    if (memberOpt.isPresent()) {
      com.scanales.eventflow.model.CommunityMember member = memberOpt.get();
      profile.displayName = member.getDisplayName() != null ? member.getDisplayName() : profile.displayName;
      profile.avatarUrl = member.getAvatarUrl();

      // Map Role/Activity to Gamification Stats
      // This is a heuristic mapping since CommunityMember might not have raw XP
      // fields yet.
      // We can infer some stats from their role or existence.

      boolean isAdmin = "administrador".equalsIgnoreCase(member.getRole());
      profile.level = isAdmin ? 10 : 5; // Admins level 10, Members level 5
      profile.experience = isAdmin ? 1000 : 500;
      profile.contributions = isAdmin ? 50 : 10; // Placeholder until we have real contribution metrics
      profile.questsCompleted = isAdmin ? 20 : 5;
      profile.eventsAttended = 5; // Default for members
      profile.projectsHosted = isAdmin ? 5 : 0;
      profile.connections = 10;
    } else {
      // Authenticated but not in community list
      profile.level = 2; // Visitor Level
      profile.experience = 100;
      profile.contributions = 0;
      profile.questsCompleted = 0;
      profile.eventsAttended = 0;
      profile.projectsHosted = 0;
      profile.connections = 0;
    }

    return Response.ok(profile).build();
  }
}
