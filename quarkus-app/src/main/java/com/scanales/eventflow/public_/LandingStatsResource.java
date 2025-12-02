package com.scanales.eventflow.public_;

import com.scanales.eventflow.service.CommunityService;
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

  @GET
  public LandingStats getStats() {
    LandingStats stats = new LandingStats();

    stats.totalMembers = communityService.countMembers();

    // NOTE: valores dummy por ahora.
    // Cuando exista un servicio de dominio para miembros/proyectos/quests,
    // reemplazar por consultas reales.
    stats.totalXP = 1337L; // TODO: reemplazar por suma real de XP
    stats.totalQuests = 7L; // TODO: reemplazar por quests reales completadas
    stats.totalProjects = 3L; // TODO: reemplazar por proyectos activos reales

    return stats;
  }

  public static class LandingStats {
    public long totalMembers;
    public long totalXP;
    public long totalQuests;
    public long totalProjects;
  }
}
