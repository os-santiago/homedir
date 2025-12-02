package com.scanales.eventflow.public_;

import com.scanales.eventflow.service.UsageMetricsService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
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

@Path("/")
public class HomeResource {

  @Inject UsageMetricsService metrics;

  @CheckedTemplate(basePath = "")
  static class Templates {
    static native TemplateInstance index();
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance home(
      @jakarta.ws.rs.core.Context HttpHeaders headers,
      @jakarta.ws.rs.core.Context RoutingContext context) {
    metrics.recordPageView("/", headers, context);
    return Templates.index();
  }

  @GET
  @Path("/events")
  @PermitAll
  public Response legacyEvents() {
    return Response.status(Response.Status.MOVED_PERMANENTLY).location(URI.create("/")).build();
  }
}
