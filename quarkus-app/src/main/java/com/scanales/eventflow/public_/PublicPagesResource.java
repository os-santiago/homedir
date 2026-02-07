package com.scanales.eventflow.public_;

import com.scanales.eventflow.community.CommunityContentItem;
import com.scanales.eventflow.community.CommunityContentService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.public_.view.ProjectsViewModel;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.GithubService;
import com.scanales.eventflow.service.GithubService.GithubContributor;
import com.scanales.eventflow.service.UserSessionService;
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
import java.time.LocalDate;
import java.util.List;
import org.jboss.logging.Logger;

@Path("/")
@Produces(MediaType.TEXT_HTML)
public class PublicPagesResource {

  private static final Logger LOG = Logger.getLogger(PublicPagesResource.class);

  @Inject
  Template home;

  @Inject
  Template projects;

  @Inject
  Template events;

  @Inject
  SecurityIdentity identity;

  @Inject
  UserSessionService userSessionService;

  @Inject
  GithubService githubService;

  @Inject
  EventService eventService;

  @Inject
  CommunityContentService communityContentService;

  @GET
  public TemplateInstance home() {
    List<GithubContributor> contributors = githubService.fetchContributors("os-santiago", "homedir");
    List<GithubContributor> projectHighlights = contributors.stream().limit(6).toList();
    int contributionTotal = contributors.stream().mapToInt(GithubContributor::contributions).sum();

    List<Event> popularEvents = eventService.findUpcomingEvents(3);
    int upcomingCount =
        (int)
            eventService.listEvents().stream()
                .filter(event -> event.getDate() != null)
                .filter(event -> !event.getDate().isBefore(LocalDate.now()))
                .count();

    List<CommunityContentItem> socialHighlights = communityContentService.listNew(3, 0);
    int socialHighlightsCount = communityContentService.metrics().cacheSize();

    if (contributors.isEmpty()) {
      LOG.debug("No contributors available for home page.");
    }

    return withLayoutData(home
        .data("pageTitle", "HomeDir - Comunidad Open Source")
        .data("pageDescription",
            "Únete a la comunidad de tecnología más activa de Santiago. Eventos, proyectos open source y crecimiento profesional gamificado.")
        .data("topContributors", contributors)
        .data("projectHighlights", projectHighlights)
        .data("popularEvents", popularEvents)
        .data("socialHighlights", socialHighlights)
        .data("socialHighlightsCount", socialHighlightsCount)
        .data("upcomingCount", upcomingCount)
        .data("projectContributorCount", contributors.size())
        .data("projectContributionTotal", contributionTotal),
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
    List<Event> upcoming = eventService.findUpcomingEvents(10);
    List<Event> past = eventService.findPastEvents(10);
    return withLayoutData(
        events
            .data("pageTitle", "Eventos - HomeDir")
            .data("pageDescription",
                "Participa en nuestros meetups, talleres y conferencias. Conecta con otros desarrolladores y aprende nuevas tecnologías.")
            .data("today", java.time.LocalDate.now())
            .data("upcomingEvents", upcoming)
            .data("pastEvents", past)
            .data("upcomingCount", upcoming.size())
            .data("pastCount", past.size()),
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
