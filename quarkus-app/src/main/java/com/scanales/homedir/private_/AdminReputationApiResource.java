package com.scanales.homedir.private_;

import com.scanales.homedir.reputation.ReputationPhase0BaselineService;
import com.scanales.homedir.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Hidden admin API for phase-0 Reputation Hub baseline and taxonomy checks. */
@Path("/api/private/admin/reputation")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AdminReputationApiResource {

  @Inject SecurityIdentity identity;

  @Inject ReputationPhase0BaselineService baselineService;

  @GET
  @Path("phase0")
  public Response phase0() {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return Response.ok(baselineService.snapshot()).build();
  }
}
