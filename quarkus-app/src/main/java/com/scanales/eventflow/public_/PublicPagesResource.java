package com.scanales.eventflow.public_;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@Produces(MediaType.TEXT_HTML)
public class PublicPagesResource {

  @Inject Template home;

  @Inject Template community;

  @Inject Template projects;

  @Inject Template events;

  @GET
  public TemplateInstance home() {
    return home.data("pageTitle", "Home");
  }

  @GET
  @Path("/community")
  public TemplateInstance community() {
    return community.data("pageTitle", "Comunidad");
  }

  @GET
  @Path("/projects")
  public TemplateInstance projects() {
    return projects.data("pageTitle", "Proyectos");
  }

  @GET
  @Path("/events")
  public TemplateInstance events() {
    return events.data("pageTitle", "Eventos");
  }
}
