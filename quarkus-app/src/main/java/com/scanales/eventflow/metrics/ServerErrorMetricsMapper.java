package com.scanales.eventflow.metrics;

import com.scanales.eventflow.service.UsageMetricsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ServerErrorMetricsMapper {

  @Inject UsageMetricsService metrics;

  @ServerExceptionMapper
  public Response map(Throwable t, UriInfo uri) {
    metrics.recordServerError("/" + uri.getPath());
    return Response.serverError().build();
  }
}
