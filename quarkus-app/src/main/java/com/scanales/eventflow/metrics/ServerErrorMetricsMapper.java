package com.scanales.eventflow.metrics;

import com.scanales.eventflow.service.UsageMetricsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ServerErrorMetricsMapper {

  private static final Logger LOG = Logger.getLogger(ServerErrorMetricsMapper.class);

  @Inject UsageMetricsService metrics;

  @ServerExceptionMapper
  public Response map(Throwable t, UriInfo uri) {
    String path = "/" + uri.getPath();
    if (t instanceof WebApplicationException webEx) {
      Response webResponse = webEx.getResponse();
      int status = webResponse != null ? webResponse.getStatus() : 500;
      if (status >= 500) {
        LOG.errorf(t, "Server error while processing %s", uri.getPath());
        metrics.recordServerError(path);
      } else {
        LOG.debugf("HTTP %d while processing %s: %s", status, uri.getPath(), t.getMessage());
      }
      return webResponse != null ? webResponse : Response.status(status).build();
    }
    LOG.errorf(t, "Server error while processing %s", uri.getPath());
    metrics.recordServerError(path);
    return Response.serverError().build();
  }
}
