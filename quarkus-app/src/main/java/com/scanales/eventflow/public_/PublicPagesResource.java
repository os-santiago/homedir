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
import jakarta.ws.rs.core.Response;
import java.net.URI;

@Path("/")
@Produces(MediaType.TEXT_HTML)
public class PublicPagesResource {

  @Inject
  Template home;

  @Inject
  Template community;

  @Inject
  Template projects;

  @Inject
  Template events;

  @Inject
  SecurityIdentity identity;

  @Inject
  com.scanales.eventflow.service.UserSessionService userSessionService;

  @GET
  public TemplateInstance home() {
    return withLayoutData(home
        .data("pageTitle", "HomeDir - Comunidad Open Source")
        .data("pageDescription",
            "Únete a la comunidad de tecnología más activa de Santiago. Eventos, proyectos open source y crecimiento profesional gamificado."),
        "home");
  }

  @GET
  @Path("/community")
  public Response community() {
    return Response.seeOther(URI.create("/comunidad")).build();
  }

  @GET
  @Path("/projects")
  public TemplateInstance projects() {
    ProjectsViewModel vm = ProjectsViewModel.mock();
    return withLayoutData(
        projects
            .data("pageTitle", "Proyectos - HomeDir")
            .data("pageDescription",
                "Explora y colabora en proyectos Open Source de la comunidad. Gana experiencia y contribuye al ecosistema.")
            .data("vm", vm),
        "proyectos");
  }

  @GET
  @Path("/events")
  public TemplateInstance events() {
    return withLayoutData(
        events
            .data("pageTitle", "Eventos - HomeDir")
            .data("pageDescription",
                "Participa en nuestros meetups, talleres y conferencias. Conecta con otros desarrolladores y aprende nuevas tecnologías.")
            .data("today", java.time.LocalDate.now()),
        "eventos");
  }

  private TemplateInstance withLayoutData(TemplateInstance templateInstance, String activePage) {
    boolean authenticated = identity != null && !identity.isAnonymous();
    String userName = authenticated ? identity.getPrincipal().getName() : "";
    String userInitial = initialFrom(userName);
    return templateInstance
        .data("activePage", activePage)
        .data("userAuthenticated", authenticated)
        .data("userName", userName != null ? userName : "")
        .data("userSession", userSessionService.getCurrentSession())
        .data("userInitial", userInitial != null ? userInitial : "");
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
