package com.scanales.eventflow.auth;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import org.eclipse.microprofile.config.ConfigProvider;

/** Login page served at /ingresar. */
@Path("/ingresar")
public class LoginPageResource {

  @Inject SecurityIdentity identity;

  @CheckedTemplate(basePath = "LoginPage")
  static class Templates {
    static native TemplateInstance ingresar(
        boolean localEnabled, boolean loginError, String localUsersDescription);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response ingresar(@QueryParam("error") String error) {
    if (identity != null && !identity.isAnonymous()) {
      return Response.seeOther(URI.create("/profile")).build();
    }
    boolean loginError = error != null;
    boolean localEnabled =
        ConfigProvider.getConfig()
            .getOptionalValue("app.auth.local-enabled", Boolean.class)
            .orElse(false);
    String localUsersDescription =
        ConfigProvider.getConfig()
            .getOptionalValue("app.auth.local-users", String.class)
            .orElse("");
    return Response.ok(Templates.ingresar(localEnabled, loginError, localUsersDescription))
        .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
        .header("Pragma", "no-cache")
        .header("Vary", "Cookie")
        .build();
  }
}
