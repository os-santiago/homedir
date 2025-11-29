package com.scanales.eventflow.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.scanales.eventflow.service.UsageMetricsService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

class ServerErrorMetricsMapperTest {

  @Test
  void recordsServerErrorAndReturns500() {
    UsageMetricsService metrics = mock(UsageMetricsService.class);
    UriInfo uri = mock(UriInfo.class);
    when(uri.getPath()).thenReturn("api/test");

    ServerErrorMetricsMapper mapper = new ServerErrorMetricsMapper();
    mapper.metrics = metrics;

    Response r = mapper.map(new RuntimeException("boom"), uri);

    verify(metrics).recordServerError("/api/test");
    assertEquals(500, r.getStatus());
  }
}
