package com.scanales.eventflow.public_;

import com.scanales.eventflow.public_.view.CommunityViewModel;
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
    return home.data("pageTitle", "Home").data("activePage", "home");
  }

  @GET
  @Path("/community")
  public TemplateInstance community() {
    CommunityViewModel vm = CommunityViewModel.mock();
    return community
        .data("pageTitle", "Comunidad")
        .data("vm", vm)
        .data("activePage", "community");
  }

  @GET
  @Path("/projects")
  public TemplateInstance projects() {
    return projects.data("pageTitle", "Proyectos").data("activePage", "projects");
  }

  @GET
  @Path("/events")
  public TemplateInstance events() {
    return events.data("pageTitle", "Eventos").data("activePage", "events");
  }
}
