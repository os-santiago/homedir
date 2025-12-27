package com.scanales.eventflow.public_;

import com.scanales.eventflow.public_.view.CommunityViewModel;
import com.scanales.eventflow.public_.view.ProjectsViewModel;
import io.quarkus.qute.Location;
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

  @Inject
  Template home;

  @Inject
  @Location("pages/comunidad.html")
  Template communityPage;

  @Inject
  @Location("ProjectsResource/proyectos.html")
  Template projectsPage;

  @Inject
  @Location("pages/eventos.html")
  Template eventsPage;

  @Inject
  SecurityIdentity identity;

  @Inject
  com.scanales.eventflow.service.UserSessionService userSessionService;

  @Inject
  com.scanales.eventflow.config.AppMessages messages;

  @Inject
  io.vertx.core.http.HttpServerRequest request;

  @GET
  public TemplateInstance home() {
    return withLayoutData(home.data("pageTitle", "Home"), "home");
  }

  @GET
  @Path("/community")
  public TemplateInstance community() {
    CommunityViewModel vm = CommunityViewModel.mock();
    return withLayoutData(
        communityPage.data("pageTitle", "Comunidad").data("vm", vm), "comunidad");
  }

  @GET
  @Path("/projects")
  public TemplateInstance projects() {
    ProjectsViewModel vm = ProjectsViewModel.mock();
    return withLayoutData(
        projectsPage.data("pageTitle", "Proyectos").data("vm", vm), "proyectos");
  }

  @GET
  @Path("/events")
  public TemplateInstance events() {
    return withLayoutData(
        eventsPage.data("pageTitle", "Eventos").data("today", java.time.LocalDate.now()), "eventos");
  }

  private TemplateInstance withLayoutData(TemplateInstance templateInstance, String activePage) {
    boolean authenticated = identity != null && !identity.isAnonymous();
    String userName = authenticated ? identity.getPrincipal().getName() : "";
    String userInitial = initialFrom(userName);

    // Locale Resolution: Cookie -> Accept-Language -> Default 'es'
    String lang = "es";
    io.vertx.core.http.Cookie localeCookie = request.getCookie("QP_LOCALE");
    if (localeCookie != null && (localeCookie.getValue().equals("en") || localeCookie.getValue().equals("es"))) {
      lang = localeCookie.getValue();
    }

    return templateInstance
        .data("activePage", activePage)
        .data("userAuthenticated", authenticated)
        .data("userName", userName != null ? userName : "")
        .data("userSession", userSessionService.getCurrentSession())
        .data("userInitial", userInitial != null ? userInitial : "")
        .data("i18n", messages)
        .data("currentLanguage", lang)
        .data("locale", java.util.Locale.forLanguageTag(lang));
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
