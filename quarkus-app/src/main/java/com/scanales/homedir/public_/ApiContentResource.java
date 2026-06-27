package com.scanales.homedir.public_;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/content")
@Produces(MediaType.APPLICATION_JSON)
public class ApiContentResource {

  @Inject CommunityContentApiResource delegate;

  @GET
  public Response list(
      @QueryParam("view") String viewParam,
      @QueryParam("filter") String filterParam,
      @QueryParam("media") String mediaParam,
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    return delegate.list(viewParam, filterParam, mediaParam, limitParam, offsetParam);
  }
}
