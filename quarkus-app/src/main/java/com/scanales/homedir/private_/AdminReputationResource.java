package com.scanales.homedir.private_;

import com.scanales.homedir.util.AdminUtils;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Admin HTML surface for Reputation Hub rollout evidence. */
@Path("/private/admin/reputation")
public class AdminReputationResource {

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance index();
  }

  @Inject SecurityIdentity identity;

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response index() {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return Response.ok(Templates.index()).build();
  }
}
