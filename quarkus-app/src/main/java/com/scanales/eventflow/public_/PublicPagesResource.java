package com.scanales.eventflow.public_;

import com.scanales.eventflow.community.CommunityContentItem;
import com.scanales.eventflow.community.CommunityContentService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.GithubService;
import com.scanales.eventflow.service.GithubService.GithubContributor;
import com.scanales.eventflow.service.UserSessionService;
import com.scanales.eventflow.util.AdminUtils;
import com.scanales.eventflow.util.TemplateLocaleUtil;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.jboss.logging.Logger;

@Path("/")
@Produces(MediaType.TEXT_HTML)
public class PublicPagesResource {

  private static final Logger LOG = Logger.getLogger(PublicPagesResource.class);

  @Inject
  Template home;

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

  @Inject
  GamificationService gamificationService;

  @GET
  public TemplateInstance home(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers) {
    currentUserId().ifPresent(userId -> gamificationService.award(userId, GamificationActivity.HOME_VIEW));
    List<GithubContributor> contributors = githubService.fetchHomeProjectContributors();
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

    return withLayoutData(
        home.data("topContributors", contributors)
            .data("projectHighlights", projectHighlights)
            .data("popularEvents", popularEvents)
            .data("socialHighlights", socialHighlights)
            .data("socialHighlightsCount", socialHighlightsCount)
            .data("upcomingCount", upcomingCount)
            .data("projectContributorCount", contributors.size())
            .data("projectContributionTotal", contributionTotal),
        "home",
        localeCookie,
        headers);
  }

  @GET
  @Path("/community")
  public Response community() {
    return Response.seeOther(URI.create("/comunidad")).build();
  }

  @GET
  @Path("/community/feed")
  public Response communityFeed() {
    return Response.seeOther(URI.create("/comunidad/feed")).build();
  }

  @GET
  @Path("/community/picks")
  public Response communityPicks() {
    return Response.seeOther(URI.create("/comunidad/picks")).build();
  }

  @GET
  @Path("/community/board")
  public Response communityBoard() {
    return Response.seeOther(URI.create("/comunidad/board")).build();
  }

  @GET
  @Path("/community/board/{group}")
  public Response communityBoardGroup(@PathParam("group") String group) {
    return Response.seeOther(URI.create("/comunidad/board/" + group)).build();
  }

  @GET
  @Path("/projects")
  public Response projects() {
    return Response.seeOther(URI.create("/proyectos")).build();
  }

  @GET
  @Path("/events")
  public TemplateInstance events(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers) {
    currentUserId().ifPresent(userId -> gamificationService.award(userId, GamificationActivity.EVENT_DIRECTORY_VIEW));
    List<Event> upcoming = eventService.findUpcomingEvents(10);
    List<Event> past = eventService.findPastEvents(10);
    return withLayoutData(
        events
            .data("today", java.time.LocalDate.now())
            .data("upcomingEvents", upcoming)
            .data("pastEvents", past)
            .data("upcomingCount", upcoming.size())
            .data("pastCount", past.size()),
        "eventos",
        localeCookie,
        headers);
  }

  private TemplateInstance withLayoutData(
      TemplateInstance templateInstance,
      String activePage,
      String localeCookie,
      jakarta.ws.rs.core.HttpHeaders headers) {
    boolean authenticated = identity != null && !identity.isAnonymous();
    String userName = authenticated ? identity.getPrincipal().getName() : "";
    String userInitial = initialFrom(userName);
    return TemplateLocaleUtil.apply(templateInstance, localeCookie, headers)
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

  private java.util.Optional<String> currentUserId() {
    if (identity == null || identity.isAnonymous()) {
      return java.util.Optional.empty();
    }
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return java.util.Optional.of(email.toLowerCase(Locale.ROOT));
    }
    String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    if (principal != null && !principal.isBlank()) {
      return java.util.Optional.of(principal.toLowerCase(Locale.ROOT));
    }
    return java.util.Optional.empty();
  }
}
