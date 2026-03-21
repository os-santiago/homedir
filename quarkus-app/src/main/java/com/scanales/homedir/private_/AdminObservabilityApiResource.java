package com.scanales.homedir.private_;

import com.scanales.homedir.observability.BusinessObservabilityService;
import com.scanales.homedir.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Hidden admin API for unified business observability snapshots. */
@Path("/api/private/admin/observability")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AdminObservabilityApiResource {

  @Inject SecurityIdentity identity;

  @Inject BusinessObservabilityService businessObservabilityService;

  @GET
  @Path("dashboard")
  public Response dashboard(@QueryParam("hours") @DefaultValue("24") int hours) {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    int safeHours = Math.max(6, Math.min(hours, 72));
    return Response.ok(businessObservabilityService.dashboard(safeHours)).build();
  }
}
