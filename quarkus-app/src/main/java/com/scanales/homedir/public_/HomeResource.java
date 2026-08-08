package com.scanales.homedir.public_;

import com.scanales.homedir.service.UsageMetricsService;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;

@Path("/legacy-home")
public class HomeResource {

  @Inject UsageMetricsService metrics;

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public Response home(
      @jakarta.ws.rs.core.Context HttpHeaders headers,
      @jakarta.ws.rs.core.Context RoutingContext context) {
    metrics.recordPageView("/legacy-home", headers, context);
    return Response.status(Response.Status.MOVED_PERMANENTLY).location(URI.create("/")).build();
  }

  @GET
  @Path("/events")
  @PermitAll
  public Response legacyEvents() {
    return Response.status(Response.Status.MOVED_PERMANENTLY).location(URI.create("/")).build();
  }
}
