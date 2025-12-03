package com.scanales.eventflow.metrics;

import com.scanales.eventflow.service.UsageMetricsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ServerErrorMetricsMapper {

  private static final Logger LOG = Logger.getLogger(ServerErrorMetricsMapper.class);

  @Inject UsageMetricsService metrics;

  @ServerExceptionMapper
  public Response map(Throwable t, UriInfo uri) {
    LOG.errorf(t, "Server error while processing %s", uri.getPath());
    metrics.recordServerError("/" + uri.getPath());
    return Response.serverError().build();
  }
}
