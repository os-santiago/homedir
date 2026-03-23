package com.scanales.homedir.private_;

import com.scanales.homedir.reputation.ReputationPhase0BaselineService;
import com.scanales.homedir.reputation.ReputationShadowReadService;
import com.scanales.homedir.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/** Hidden admin API for phase-0 Reputation Hub baseline and taxonomy checks. */
@Path("/api/private/admin/reputation")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AdminReputationApiResource {

  @Inject SecurityIdentity identity;

  @Inject ReputationPhase0BaselineService baselineService;
  @Inject ReputationShadowReadService shadowReadService;

  @GET
  @Path("phase0")
  public Response phase0() {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return Response.ok(baselineService.snapshot()).build();
  }

  @GET
  @Path("phase2/diagnostics")
  public Response phase2Diagnostics() {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return shadowReadService
        .diagnostics()
        .map(payload -> Response.ok(payload).build())
        .orElseGet(
            () ->
                Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "reputation_shadow_read_disabled"))
                    .build());
  }

  @GET
  @Path("phase2/user/{userId}")
  public Response phase2User(@PathParam("userId") String userId, @QueryParam("limit") Integer limit) {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return shadowReadService
        .user(userId, limit)
        .map(payload -> Response.ok(payload).build())
        .orElseGet(
            () ->
                Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "reputation_shadow_read_disabled"))
                    .build());
  }
}
