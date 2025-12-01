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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@PermitAll
@Produces(MediaType.TEXT_HTML)
public class MarketingPagesResource {

  @Inject UsageMetricsService metrics;

  @CheckedTemplate(basePath = "pages")
  static class Templates {
    public static native TemplateInstance docs();

    public static native TemplateInstance contacto();
  }

  @GET
  @Path("/docs")
  public TemplateInstance docs(
      @Context HttpHeaders headers, @Context RoutingContext context) {
    metrics.recordPageView("/docs", headers, context);
    return Templates.docs();
  }

  @GET
  @Path("/contacto")
  public TemplateInstance contacto(
      @Context HttpHeaders headers, @Context RoutingContext context) {
    metrics.recordPageView("/contacto", headers, context);
    return Templates.contacto();
  }
}
