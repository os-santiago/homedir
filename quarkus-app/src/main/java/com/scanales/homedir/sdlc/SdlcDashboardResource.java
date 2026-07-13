package com.scanales.homedir.sdlc;

import com.scanales.homedir.util.AdminUtils;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/sdlc/dashboard")
@Authenticated
public class SdlcDashboardResource {
  @Inject SecurityIdentity identity;

  @Inject
  @Location("sdlc/dashboard/index.qute.html")
  Template dashboard;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Object dashboard() {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return dashboard.instance();
  }
}
