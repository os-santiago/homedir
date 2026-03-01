package com.scanales.eventflow.public_;

import com.scanales.eventflow.community.CommunityBoardService;
import com.scanales.eventflow.community.CommunityBoardSummary;
import com.scanales.eventflow.community.CommunityContentItem;
import com.scanales.eventflow.community.CommunityContentService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.GithubService;
import com.scanales.eventflow.service.GithubService.GithubContributor;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/beta")
public class BetaResource {

  @Inject SecurityIdentity identity;
  @Inject UsageMetricsService metrics;
  @Inject CommunityContentService communityContentService;
  @Inject CommunityBoardService communityBoardService;
  @Inject EventService eventService;
  @Inject GithubService githubService;

  @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
  String runtimeVersion;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance beta(boolean isAuthenticated, String userName, BetaWorldView world);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance view(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context HttpHeaders headers,
      @jakarta.ws.rs.core.Context RoutingContext context) {
    metrics.recordPageView("/beta", headers, context);
    boolean authenticated = identity != null && !identity.isAnonymous();
    String userName =
        authenticated && identity.getPrincipal() != null ? identity.getPrincipal().getName() : "";
    BetaWorldView world = buildWorld();
    return TemplateLocaleUtil.apply(Templates.beta(authenticated, userName, world), localeCookie, headers);
  }

  private BetaWorldView buildWorld() {
    List<CommunityContentItem> picksSource = communityContentService.listNew(3, 0);
    List<WorldPick> guildPicks =
        picksSource.stream()
            .limit(3)
            .map(
                item ->
                    new WorldPick(
                        defaultText(item.title(), "Untitled pick"),
                        clip(defaultText(item.summary(), "No summary available yet."), 120),
                        defaultText(item.source(), "Source"),
                        defaultText(item.mediaType(), "article"),
                        defaultText(item.url(), "/comunidad/picks")))
            .toList();

    List<Event> upcomingEvents = eventService.listUpcomingEvents();
    List<WorldEvent> theaterEvents = upcomingEvents.stream().limit(3).map(this::toWorldEvent).toList();

    List<GithubContributor> contributors = githubService.fetchHomeProjectContributors();
    List<WorldContributor> cityhallContributors =
        contributors.stream()
            .limit(5)
            .map(
                contributor ->
                    new WorldContributor(
                        defaultText(contributor.login(), "unknown"),
                        contributor.contributions(),
                        defaultText(
                            contributor.htmlUrl(),
                            "https://github.com/os-santiago/homedir"),
                        contributor.avatarUrl(),
                        initialFrom(contributor.login())))
            .toList();

    int contributionTotal = contributors.stream().mapToInt(GithubContributor::contributions).sum();
    CommunityBoardSummary boardSummary = communityBoardService.summary();
    if (boardSummary == null) {
      boardSummary = new CommunityBoardSummary(0, 0, 0, 0, 0, 0, null, null, null);
    }

    WorldPulse pulse =
        new WorldPulse(
            communityContentService.metrics().cacheSize(),
            upcomingEvents.size(),
            boardSummary.homedirUsers(),
            contributionTotal,
            cityhallContributors.size());

    return new BetaWorldView(
        pulse,
        guildPicks,
        theaterEvents,
        cityhallContributors,
        boardSummary,
        defaultText(runtimeVersion, "dev"));
  }

  private WorldEvent toWorldEvent(Event event) {
    if (event == null) {
      return new WorldEvent("TBD", "TBD", "TBD", "/eventos", null);
    }
    String date = event.getDateStr() == null || event.getDateStr().isBlank() ? "TBD" : event.getDateStr();
    String countdown = event.isOngoing() ? "LIVE" : "D-" + event.getDaysUntil();
    return new WorldEvent(
        defaultText(event.getTitle(), "Untitled event"),
        date,
        countdown,
        "/eventos",
        event.getLogoUrl());
  }

  private String defaultText(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private String clip(String value, int max) {
    if (value == null) {
      return "";
    }
    String text = value.trim();
    if (text.length() <= max) {
      return text;
    }
    return text.substring(0, Math.max(0, max - 1)).trim() + "…";
  }

  private String initialFrom(String value) {
    if (value == null || value.isBlank()) {
      return "HD";
    }
    return value.substring(0, 1).toUpperCase();
  }

  public record BetaWorldView(
      WorldPulse pulse,
      List<WorldPick> guildPicks,
      List<WorldEvent> theaterEvents,
      List<WorldContributor> cityhallContributors,
      CommunityBoardSummary boardSummary,
      String releaseVersion) {}

  public record WorldPulse(
      int curatedItems,
      int upcomingEvents,
      int homedirUsers,
      int totalContributions,
      int trackedContributors) {}

  public record WorldPick(
      String title,
      String summary,
      String source,
      String mediaType,
      String href) {}

  public record WorldEvent(
      String title,
      String dateLabel,
      String countdownLabel,
      String href,
      String logoUrl) {}

  public record WorldContributor(
      String login,
      int contributions,
      String href,
      String avatarUrl,
      String initial) {}
}
