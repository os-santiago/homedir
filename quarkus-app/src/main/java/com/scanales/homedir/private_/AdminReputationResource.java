package com.scanales.homedir.private_;

import com.scanales.homedir.reputation.ReputationGaObservationJournalService;
import com.scanales.homedir.util.AdminUtils;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

/** Admin HTML surface for Reputation Hub rollout evidence. */
@Path("/private/admin/reputation")
public class AdminReputationResource {

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance index();
  }

  @Inject SecurityIdentity identity;
  @Inject ReputationGaObservationJournalService observationJournalService;

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response index() {
    if (!AdminUtils.canViewAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return Response.ok(Templates.index()).build();
  }

  @POST
  @Path("closeout-check/{checkCode}/ack")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Authenticated
  public Response acknowledgeCloseoutCheck(
      @PathParam("checkCode") String checkCode, @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    observationJournalService.acknowledge(checkCode, identity.getPrincipal().getName());
    return Response.seeOther(redirectUri()).build();
  }

  @POST
  @Path("closeout-check/{checkCode}/clear")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Authenticated
  public Response clearCloseoutCheck(
      @PathParam("checkCode") String checkCode, @FormParam("returnTo") String returnTo) {
    if (!AdminUtils.canManageAdminBackoffice(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    observationJournalService.clear(checkCode, identity.getPrincipal().getName());
    return Response.seeOther(redirectUri()).build();
  }

  private java.net.URI redirectUri() {
    return UriBuilder.fromPath("/private/admin/reputation").build();
  }
}
