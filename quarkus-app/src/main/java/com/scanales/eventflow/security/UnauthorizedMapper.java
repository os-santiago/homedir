package com.scanales.eventflow.security;

import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Adds headers to unauthorized API responses signalling session expiry. */
@Provider
public class UnauthorizedMapper implements ExceptionMapper<UnauthorizedException> {

  @Override
  public Response toResponse(UnauthorizedException e) {
    return Response.status(Response.Status.UNAUTHORIZED)
        .header("X-Session-Expired", "true")
        .header(
            HttpHeaders.WWW_AUTHENTICATE,
            "Bearer realm=\"eventflow\", error=\"invalid_token\", error_description=\"expired\"")
        .build();
  }
}
