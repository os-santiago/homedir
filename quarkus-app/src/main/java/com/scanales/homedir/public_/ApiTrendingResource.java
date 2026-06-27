package com.scanales.homedir.public_;

import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/trending")
@Produces(MediaType.TEXT_HTML)
public class ApiTrendingResource {

  @Inject TrendingResource delegate;

  @GET
  public TemplateInstance index(
      @QueryParam("period") String periodParam,
      @QueryParam("count") Integer countParam,
      @CookieParam("QP_LOCALE") String localeCookie) {
    return delegate.index(periodParam, countParam, localeCookie);
  }
}
