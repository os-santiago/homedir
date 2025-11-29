package com.scanales.eventflow.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RateLimitingFilterTest {

  private RateLimitingFilter filter;

  @BeforeEach
  void setUp() throws Exception {
    filter = new RateLimitingFilter();
    setField("enabled", true);
    setField("windowSeconds", 60);
    setField("authLimit", 2);
    setField("logoutLimit", 1);
    setField("apiLimit", 2);
  }

  @Test
  void throttlesAuthBucketAfterLimit() {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    UriInfo uri = mock(UriInfo.class);
    when(uri.getPath()).thenReturn("login");
    when(ctx.getUriInfo()).thenReturn(uri);
    when(ctx.getHeaderString("X-Forwarded-For")).thenReturn("1.1.1.1");

    // First two pass
    filter.filter(ctx);
    filter.filter(ctx);

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
    filter.filter(ctx);

    verify(ctx, times(1)).abortWith(captor.capture());
    assertEquals(429, captor.getValue().getStatus());
  }

  @Test
  void skipsStaticAssets() {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    UriInfo uri = mock(UriInfo.class);
    when(uri.getPath()).thenReturn("css/app.css");
    when(ctx.getUriInfo()).thenReturn(uri);

    filter.filter(ctx);

    verify(ctx, never()).abortWith(any());
  }

  private void setField(String name, Object value) throws Exception {
    Field f = RateLimitingFilter.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(filter, value);
  }
}
