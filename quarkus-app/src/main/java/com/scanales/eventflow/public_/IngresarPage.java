package com.scanales.eventflow.public_;

import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Alias for the login page at /ingresar. */
@Path("/ingresar")
public class IngresarPage {

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance login(@jakarta.ws.rs.QueryParam("redirect") String redirect) {
    return LoginPage.Templates.login(redirect);
  }
}
