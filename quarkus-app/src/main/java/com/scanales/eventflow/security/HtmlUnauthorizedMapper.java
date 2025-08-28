package com.scanales.eventflow.security;

import io.quarkus.security.UnauthorizedException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;
import java.util.Optional;

/** Redirects HTML requests on 401 while keeping API responses with headers. */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class HtmlUnauthorizedMapper implements ExceptionMapper<UnauthorizedException> {

  @Context HttpHeaders headers;

  @Override
  public Response toResponse(UnauthorizedException e) {
    String accept = Optional.ofNullable(headers.getHeaderString(HttpHeaders.ACCEPT)).orElse("");
    if (accept.contains(MediaType.TEXT_HTML)) {
      return Response.seeOther(URI.create("/?session=expired")).build();
    }
    return Response.status(Response.Status.UNAUTHORIZED)
        .header("X-Session-Expired", "true")
        .header(
            HttpHeaders.WWW_AUTHENTICATE,
            "Bearer realm=\"eventflow\", error=\"invalid_token\", error_description=\"expired\"")
        .build();
  }
}
