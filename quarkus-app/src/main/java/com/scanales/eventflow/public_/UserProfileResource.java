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

  @Inject SecurityIdentity identity;

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
    profile.displayName = identity.getPrincipal().getName(); // TODO: mapear a nombre “bonito” si existe
    profile.avatarUrl = null; // TODO: integrar con perfil/avatars reales si existen

    // TODO: si ya existe algún servicio o entidad con stats del usuario,
    // úsalo aquí en vez de valores dummy.
    profile.level = 5;
    profile.experience = 420;
    profile.contributions = 10;
    profile.questsCompleted = 3;
    profile.eventsAttended = 2;
    profile.projectsHosted = 1;
    profile.connections = 8;

    return Response.ok(profile).build();
  }
}
