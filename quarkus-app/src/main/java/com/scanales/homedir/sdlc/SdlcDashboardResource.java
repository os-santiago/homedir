package com.scanales.homedir.sdlc;

import com.scanales.homedir.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;

@Path("/sdlc/dashboard")
@Authenticated
public class SdlcDashboardResource {
  @Inject SecurityIdentity identity;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response dashboard() {
    if (!AdminUtils.canViewAdminBackoffice(identity)) return Response.status(Response.Status.FORBIDDEN).build();
    InputStream stream = getClass().getResourceAsStream("/META-INF/resources/sdlc/dashboard/index.html");
    return stream == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(stream).build();
  }
}
