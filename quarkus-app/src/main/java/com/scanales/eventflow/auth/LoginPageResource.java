package com.scanales.eventflow.auth;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;

/** Login page served at /ingresar. */
@Path("/ingresar")
public class LoginPageResource {

  @Inject SecurityIdentity identity;

  @CheckedTemplate(basePath = "LoginPage")
  static class Templates {
    static native TemplateInstance ingresar();
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response ingresar() {
    if (identity != null && !identity.isAnonymous()) {
      return Response.seeOther(URI.create("/profile")).build();
    }
    return Response.ok(Templates.ingresar())
        .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
        .header("Pragma", "no-cache")
        .header("Vary", "Cookie")
        .build();
  }
}
