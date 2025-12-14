package com.scanales.eventflow.public_;

import com.scanales.eventflow.public_.landing.LandingService;
import com.scanales.eventflow.public_.landing.LandingViewModel;
import com.scanales.eventflow.service.UsageMetricsService;
import io.quarkus.qute.Template;
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

@Path("/legacy-home")
public class HomeResource {

  @Inject
  UsageMetricsService metrics;

  @Inject
  Template index;

  @Inject

  LandingService landingService;

  @Inject
  com.scanales.eventflow.service.UserSessionService userSessionService;

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance home(
      @jakarta.ws.rs.core.Context HttpHeaders headers,
      @jakarta.ws.rs.core.Context RoutingContext context) {
    // TODO: Legacy landing page kept for reference; main public routes moved to
    // PublicPagesResource.
    metrics.recordPageView("/legacy-home", headers, context);
    LandingViewModel viewModel = landingService.buildViewModel();
    return index
        .data("vm", viewModel)
        .data("userSession", userSessionService.getCurrentSession())
        .data("loginUrl", "/private/profile")
        .data("logoutUrl", "/logout");
  }

  @GET
  @Path("/events")
  @PermitAll
  public Response legacyEvents() {
    return Response.status(Response.Status.MOVED_PERMANENTLY).location(URI.create("/")).build();
  }
}
