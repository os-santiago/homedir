package com.scanales.eventflow.public_;

import com.scanales.eventflow.public_.view.CommunityViewModel;
import com.scanales.eventflow.public_.view.ProjectsViewModel;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
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

  @Inject SecurityIdentity identity;

  @GET
  public TemplateInstance home() {
    return withLayoutData(home.data("pageTitle", "Home"), "home");
  }

  @GET
  @Path("/community")
  public TemplateInstance community() {
    CommunityViewModel vm = CommunityViewModel.mock();
    return withLayoutData(
        community.data("pageTitle", "Comunidad").data("vm", vm), "comunidad");
  }

  @GET
  @Path("/projects")
  public TemplateInstance projects() {
    ProjectsViewModel vm = ProjectsViewModel.mock();
    return withLayoutData(
        projects.data("pageTitle", "Proyectos").data("vm", vm), "proyectos");
  }

  @GET
  @Path("/events")
  public TemplateInstance events() {
    return withLayoutData(events.data("pageTitle", "Eventos"), "eventos");
  }

  private TemplateInstance withLayoutData(TemplateInstance templateInstance, String activePage) {
    boolean authenticated = identity != null && !identity.isAnonymous();
    String userName = authenticated ? identity.getPrincipal().getName() : null;
    return templateInstance
        .data("activePage", activePage)
        .data("userAuthenticated", authenticated)
        .data("userName", userName)
        .data("userInitial", initialFrom(userName));
  }

  private String initialFrom(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.substring(0, 1).toUpperCase();
  }
}
