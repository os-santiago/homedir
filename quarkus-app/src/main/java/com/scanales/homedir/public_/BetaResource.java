package com.scanales.homedir.public_;

import com.scanales.homedir.community.CommunityBoardService;
import com.scanales.homedir.community.CommunityBoardSummary;
import com.scanales.homedir.community.CommunityContentItem;
import com.scanales.homedir.community.CommunityContentService;
import com.scanales.homedir.model.Event;
import com.scanales.homedir.model.QuestClass;
import com.scanales.homedir.model.UserSession;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.GithubService;
import com.scanales.homedir.service.GithubService.GithubContributor;
import com.scanales.homedir.service.QuestService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.service.UserSessionService;
import com.scanales.homedir.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
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

  @Inject UsageMetricsService metrics;
  @Inject CommunityContentService communityContentService;
  @Inject CommunityBoardService communityBoardService;
  @Inject EventService eventService;
  @Inject GithubService githubService;
  @Inject UserSessionService userSessionService;
  @Inject QuestService questService;

  @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
  String runtimeVersion;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance beta(BetaWorldView world);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance view(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context HttpHeaders headers,
      @jakarta.ws.rs.core.Context RoutingContext context) {
    metrics.recordPageView("/beta", headers, context);
    UserSession session = userSessionService.getCurrentSession();
    BetaWorldView world = buildWorld(session);
    return TemplateLocaleUtil.apply(Templates.beta(world), localeCookie, headers);
  }

  private BetaWorldView buildWorld(UserSession session) {
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
        defaultText(runtimeVersion, "dev"),
        buildPlayer(session));
  }

  private PlayerProfile buildPlayer(UserSession session) {
    boolean authenticated = session != null && session.loggedIn();
    String displayName =
        authenticated ? defaultText(session.displayName(), "Player") : "Guest Adventurer";
    String avatarUrl = authenticated ? sanitizeAvatarUrl(session.avatarUrl()) : null;
    int level = authenticated ? Math.max(1, session.level()) : 1;
    int currentXp = authenticated ? Math.max(0, session.currentXp()) : 0;
    int nextLevelXp = authenticated ? Math.max(currentXp + 1, session.nextLevelXp()) : 100;
    int levelFloor = authenticated ? Math.max(0, questService.getXpForLevel(level)) : 0;
    int segmentMax = Math.max(1, nextLevelXp - levelFloor);
    int segmentCurrent = Math.max(0, Math.min(segmentMax, currentXp - levelFloor));
    int progressPercent =
        Math.max(0, Math.min(100, (int) Math.round((segmentCurrent * 100.0d) / segmentMax)));

    QuestClass questClass = authenticated ? session.questClass() : null;
    String classLabel = questClass != null ? questClass.getDisplayName() : "Adventurer";
    String classEmoji = questClass != null ? questClass.getEmoji() : "✨";

    return new PlayerProfile(
        authenticated,
        displayName,
        avatarUrl,
        initialFrom(displayName),
        level,
        currentXp,
        nextLevelXp,
        progressPercent,
        classLabel,
        classEmoji,
        "/private/login-callback?redirect=/beta");
  }

  private String sanitizeAvatarUrl(String value) {
    String candidate = value == null ? null : value.trim();
    if (candidate == null || candidate.isBlank()) {
      return null;
    }
    if (candidate.startsWith("https://") || candidate.startsWith("http://")) {
      return candidate;
    }
    return null;
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
      String releaseVersion,
      PlayerProfile player) {}

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

  public record PlayerProfile(
      boolean authenticated,
      String displayName,
      String avatarUrl,
      String avatarInitial,
      int level,
      int currentXp,
      int nextLevelXp,
      int progressPercent,
      String classLabel,
      String classEmoji,
      String loginUrl) {}
}
