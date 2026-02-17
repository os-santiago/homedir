package com.scanales.eventflow.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    setField("communityContentApiLimit", 3);
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

  @Test
  void communityContentUsesDedicatedBucketLimit() {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    UriInfo uri = mock(UriInfo.class);
    when(uri.getPath()).thenReturn("api/community/content");
    when(ctx.getUriInfo()).thenReturn(uri);
    when(ctx.getHeaderString("X-Forwarded-For")).thenReturn("2.2.2.2");

    filter.filter(ctx);
    filter.filter(ctx);
    filter.filter(ctx);

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
    filter.filter(ctx);

    verify(ctx, times(1)).abortWith(captor.capture());
    assertEquals(429, captor.getValue().getStatus());
  }

  @Test
  void usesCfConnectingIpWhenForwardedForIsMissing() {
    ContainerRequestContext ctxA = mock(ContainerRequestContext.class);
    UriInfo uriA = mock(UriInfo.class);
    when(uriA.getPath()).thenReturn("api/events");
    when(ctxA.getUriInfo()).thenReturn(uriA);
    when(ctxA.getHeaderString("X-Forwarded-For")).thenReturn(null);
    when(ctxA.getHeaderString("CF-Connecting-IP")).thenReturn("3.3.3.3");

    ContainerRequestContext ctxB = mock(ContainerRequestContext.class);
    UriInfo uriB = mock(UriInfo.class);
    when(uriB.getPath()).thenReturn("api/events");
    when(ctxB.getUriInfo()).thenReturn(uriB);
    when(ctxB.getHeaderString("X-Forwarded-For")).thenReturn(null);
    when(ctxB.getHeaderString("CF-Connecting-IP")).thenReturn("4.4.4.4");

    filter.filter(ctxA);
    filter.filter(ctxA);
    filter.filter(ctxB);
    filter.filter(ctxB);

    verify(ctxA, never()).abortWith(any());
    verify(ctxB, never()).abortWith(any());
  }

  @Test
  void exposesStatsForCheckedAndThrottledBuckets() {
    ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    UriInfo uri = mock(UriInfo.class);
    when(uri.getPath()).thenReturn("api/community/content");
    when(ctx.getUriInfo()).thenReturn(uri);
    when(ctx.getHeaderString("X-Forwarded-For")).thenReturn("5.5.5.5");

    filter.filter(ctx);
    filter.filter(ctx);
    filter.filter(ctx);
    filter.filter(ctx); // throttled

    RateLimitingFilter.RateLimitStats stats = filter.stats();
    assertEquals(4, stats.totalChecked());
    assertEquals(1, stats.totalThrottled());
    assertTrue(stats.checkedByBucket().getOrDefault("api-community-content", 0L) >= 4L);
    assertTrue(stats.throttledByBucket().getOrDefault("api-community-content", 0L) >= 1L);
  }

  private void setField(String name, Object value) throws Exception {
    Field f = RateLimitingFilter.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(filter, value);
  }
}
