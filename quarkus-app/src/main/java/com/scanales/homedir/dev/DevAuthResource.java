package com.scanales.homedir.dev;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/login.html")
@IfBuildProfile("dev")
public class DevAuthResource {

  @Inject
  @Location("dev-login.html")
  Template devLogin;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance login() {
    return devLogin.instance();
  }
}
