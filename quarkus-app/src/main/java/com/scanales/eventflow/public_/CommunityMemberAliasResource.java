package com.scanales.eventflow.public_;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@Path("/comunidad/member")
@PermitAll
public class CommunityMemberAliasResource {

  @GET
  @Path("/{group}/{id}")
  public Response redirect(@PathParam("group") String group, @PathParam("id") String id) {
    return Response.status(Response.Status.SEE_OTHER)
        .header("Location", "/community/member/" + group + "/" + id)
        .build();
  }
}
