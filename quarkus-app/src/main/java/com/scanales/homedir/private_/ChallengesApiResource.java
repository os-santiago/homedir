package com.scanales.homedir.private_;

import com.scanales.homedir.challenges.ChallengeService;
import com.scanales.homedir.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Locale;
import java.util.Map;

@Path("/api/private/challenges")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ChallengesApiResource {

  @Inject SecurityIdentity identity;
  @Inject ChallengeService challengeService;

  @GET
  @Path("me")
  public Response me() {
    String userId = currentUserId();
    if (userId == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "invalid_user", "message", "Could not resolve current user"))
          .build();
    }
    return Response.ok(Map.of("items", challengeService.listProgressForUser(userId))).build();
  }

  private String currentUserId() {
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return email.toLowerCase(Locale.ROOT);
    }
    if (identity == null || identity.getPrincipal() == null) {
      return null;
    }
    return identity.getPrincipal().getName();
  }
}
