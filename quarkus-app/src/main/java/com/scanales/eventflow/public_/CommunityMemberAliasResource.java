package com.scanales.eventflow.public_;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.net.URI;

@Path("/comunidad/member")
@PermitAll
public class CommunityMemberAliasResource {

  @GET
  @Path("/{group}/{id}")
  public Response redirect(@PathParam("group") String group, @PathParam("id") String id) {
    return Response.seeOther(URI.create("/community/member/" + group + "/" + id)).build();
  }
}
