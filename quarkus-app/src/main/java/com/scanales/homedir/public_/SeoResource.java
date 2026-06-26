package com.scanales.homedir.public_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("")
@PermitAll
public class SeoResource {

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance robots();

    static native TemplateInstance sitemap();
  }

  @GET
  @Path("/robots.txt")
  @Produces(MediaType.TEXT_PLAIN)
  public TemplateInstance robots() {
    return Templates.robots();
  }

  @GET
  @Path("/sitemap.xml")
  @Produces(MediaType.APPLICATION_XML)
  public TemplateInstance sitemap() {
    return Templates.sitemap();
  }
}
