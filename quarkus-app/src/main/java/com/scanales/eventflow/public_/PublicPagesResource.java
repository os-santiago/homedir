package com.scanales.eventflow.public_;

import com.scanales.eventflow.challenges.ChallengeService;
import com.scanales.eventflow.community.CommunityContentItem;
import com.scanales.eventflow.community.CommunityContentService;
import com.scanales.eventflow.community.CommunityBoardService;
import com.scanales.eventflow.community.CommunityBoardSummary;
import com.scanales.eventflow.community.CommunityLightningService;
import com.scanales.eventflow.community.CommunityVoteService;
import com.scanales.eventflow.economy.EconomyService;
import com.scanales.eventflow.economy.EconomyWallet;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.model.QuestClass;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.notifications.Notification;
import com.scanales.eventflow.notifications.NotificationService;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.GithubService;
import com.scanales.eventflow.service.GithubService.GithubContributor;
import com.scanales.eventflow.service.UserProfileService;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
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
  CommunityBoardService communityBoardService;

  @Inject
  CommunityLightningService communityLightningService;

  @Inject
  CommunityVoteService communityVoteService;

  @Inject
  EconomyService economyService;

  @Inject
  GamificationService gamificationService;

  @Inject
  UserProfileService userProfileService;

  @Inject
  NotificationService notificationService;

  @Inject
  ChallengeService challengeService;

  @GET
  public TemplateInstance home(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers) {
    String resolvedLocale = TemplateLocaleUtil.resolve(localeCookie, headers);
    var currentUserId = currentUserId();
    var currentSession = userSessionService.getCurrentSession();
    currentUserId.ifPresent(userId -> gamificationService.award(userId, GamificationActivity.HOME_VIEW));
    List<GithubContributor> contributors = githubService.fetchHomeProjectContributors();
    List<GithubContributor> projectHighlights = contributors.stream().limit(6).toList();
    int contributionTotal = contributors.stream().mapToInt(GithubContributor::contributions).sum();

    List<Event> upcomingEvents = eventService.listUpcomingEvents();
    List<Event> popularEvents = upcomingEvents.stream().limit(3).toList();
    int upcomingCount = upcomingEvents.size();

    List<CommunityContentItem> socialHighlights = communityContentService.listNew(3, 0);
    List<CommunityContentItem> allCommunityItems = communityContentService.allItems();
    CommunityBoardSummary boardSummary = communityBoardService.summary();
    int socialHighlightsCount = communityContentService.metrics().cacheSize();
    Instant todayCutoff = Instant.now().minus(Duration.ofHours(24));
    long recentPicksCount =
        allCommunityItems.stream()
            .filter(item -> item.createdAt() != null && !item.createdAt().isBefore(todayCutoff))
            .count();
    long recentMemberPicksCount =
        allCommunityItems.stream()
            .filter(this::isMemberOrigin)
            .filter(item -> item.createdAt() != null && !item.createdAt().isBefore(todayCutoff))
            .count();
    int recentLtaThreads = communityLightningService.countPublishedSince(todayCutoff);
    long homeStarterVoteCount =
        currentUserId.map(communityVoteService::countVotesByUser).orElse(0L);
    boolean homeStarterHasVote = homeStarterVoteCount > 0L;
    Optional<UserProfile> currentProfile = currentUserId.flatMap(userProfileService::find);
    String homeExperienceLocale =
        currentProfile
            .map(UserProfile::getPreferredLocale)
            .filter(locale -> locale != null && !locale.isBlank())
            .orElse(resolvedLocale);
    HomeChallengesCopy homeChallengesCopy = homeChallengesCopy(homeExperienceLocale);
    HomeChallengeLoopCopy homeChallengeLoopCopy = homeChallengeLoopCopy(homeExperienceLocale);
    boolean homeAccountHasGithub =
        currentProfile
            .map(profile -> profile.getGithub() != null && profile.hasGithub())
            .orElse(false);
    boolean homeAccountHasDiscord =
        currentProfile
            .map(profile -> profile.getDiscord() != null && profile.hasDiscord())
            .orElse(false);
    int homeStarterRemainingXp =
        starterXpRemaining(homeAccountHasGithub, homeAccountHasDiscord, homeStarterHasVote);
    int homeStarterRemainingHcoin = economyService.previewGamificationReward(homeStarterRemainingXp);
    int homeLinkedSignals = 1 + (homeAccountHasGithub ? 1 : 0) + (homeAccountHasDiscord ? 1 : 0);
    int homeStarterCompleted =
        (homeAccountHasGithub ? 1 : 0) + (homeAccountHasDiscord ? 1 : 0) + (homeStarterHasVote ? 1 : 0);
    long homeWalletBalance =
        currentUserId.map(economyService::getWallet).map(EconomyWallet::balanceHcoin).orElse(0L);
    int homeInventoryCount =
        currentUserId.map(userId -> economyService.listInventory(userId, 20, 0).size()).orElse(0);
    int homeRewardsCatalogCount = economyService.listCatalog().size();
    NotificationService.NotificationPage homeNotificationPage =
        currentUserId
            .map(userId -> notificationService.listPage(userId, "all", null, 3))
            .orElse(new NotificationService.NotificationPage(List.of(), null, 0L));
    int homeUnreadNotifications = toIntSafely(homeNotificationPage.unreadCount());
    List<HomeNotificationPreview> homeNotificationPreview =
        homeNotificationPage.items().stream().limit(2).map(this::toHomeNotificationPreview).toList();
    List<HomeClassProgress> homeClassProgress =
        buildHomeClassProgress(currentProfile.orElse(null), currentSession.currentXp());
    HomeClassProgress homeDominantClass =
        homeClassProgress.stream()
            .filter(progress -> progress.xp() > 0)
            .max(Comparator.comparingInt(HomeClassProgress::xp).thenComparing(HomeClassProgress::value))
            .orElse(null);
    List<EconomyService.CatalogOffer> homeCatalogOffers =
        currentUserId.map(economyService::listCatalogForUser).orElse(List.of());
    int homeUnlockedRewardCount =
        (int)
            homeCatalogOffers.stream()
                .filter(offer -> offer.remainingStock() > 0 && offer.unlocked())
                .count();
    int homeAffordableRewardCount =
        (int)
            homeCatalogOffers.stream()
                .filter(
                    offer ->
                        offer.remainingStock() > 0
                            && offer.unlocked()
                            && offer.priceHcoin() <= homeWalletBalance)
                .count();
    HomeRewardSpotlight homeRewardSpotlight =
        chooseRewardSpotlight(homeCatalogOffers, homeWalletBalance);
    List<HomeChallengeCard> homeChallengeCards =
        currentUserId
            .map(challengeService::listProgressForUser)
            .orElse(List.of())
            .stream()
            .map(card -> toHomeChallengeCard(card, homeChallengesCopy))
            .toList();
    int homeChallengesCompleted = (int) homeChallengeCards.stream().filter(HomeChallengeCard::completed).count();
    int homeChallengesInProgress =
        (int)
            homeChallengeCards.stream()
                .filter(card -> !card.completed() && card.completedSteps() > 0)
                .count();
    int homeChallengesReady = Math.max(0, homeChallengeCards.size() - homeChallengesCompleted - homeChallengesInProgress);
    List<HomeChallengeTrend> homeTrendingChallenges =
        challengeService.trendingChallenges(Duration.ofDays(7), 3).stream()
            .map(trend -> toHomeChallengeTrend(trend, homeChallengesCopy, homeChallengeLoopCopy))
            .toList();
    HomeChallengeLeaderboard homeChallengeLeaderboard =
        currentUserId
            .map(challengeService::leaderboardForUser)
            .map(rank -> toHomeChallengeLeaderboard(rank, homeChallengeLoopCopy))
            .orElse(new HomeChallengeLeaderboard(0, 0, 0, null, homeChallengeLoopCopy.leaderboardEmptyTitle(), homeChallengeLoopCopy.leaderboardEmptyDesc()));

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
            .data("projectContributionTotal", contributionTotal)
            .data("homeTodayFreshPicks", toIntSafely(recentPicksCount))
            .data("homeTodayMemberPicks", toIntSafely(recentMemberPicksCount))
            .data("homeTodayLtaThreads", recentLtaThreads)
            .data("homeAccountHasGithub", homeAccountHasGithub)
            .data("homeAccountHasDiscord", homeAccountHasDiscord)
            .data("homeLinkedSignals", homeLinkedSignals)
            .data("homeStarterHasVote", homeStarterHasVote)
            .data("homeStarterCompleted", homeStarterCompleted)
            .data("homeStarterRemainingXp", homeStarterRemainingXp)
            .data("homeStarterRemainingHcoin", homeStarterRemainingHcoin)
            .data("homeStarterAllDone", homeStarterCompleted >= 3)
            .data("homeWalletBalance", homeWalletBalance)
            .data("homeInventoryCount", homeInventoryCount)
            .data("homeRewardsCatalogCount", homeRewardsCatalogCount)
            .data("homeUnreadNotifications", homeUnreadNotifications)
            .data("homeNotificationPreview", homeNotificationPreview)
            .data("homeClassProgress", homeClassProgress)
            .data("homeDominantClass", homeDominantClass)
            .data("homeUnlockedRewardCount", homeUnlockedRewardCount)
            .data("homeAffordableRewardCount", homeAffordableRewardCount)
            .data("homeRewardSpotlight", homeRewardSpotlight)
            .data("homeChallengeCards", homeChallengeCards)
            .data("homeChallengesCompleted", homeChallengesCompleted)
            .data("homeChallengesInProgress", homeChallengesInProgress)
            .data("homeChallengesReady", homeChallengesReady)
            .data("homeChallengesCopy", homeChallengesCopy)
            .data("homeChallengeLoopCopy", homeChallengeLoopCopy)
            .data("homeTrendingChallenges", homeTrendingChallenges)
            .data("homeChallengeLeaderboard", homeChallengeLeaderboard)
            .data("homeBoardSummary", boardSummary)
            .data("noLoginModal", true),
        "home",
        resolvedLocale,
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
    List<Event> upcoming = eventService.listUpcomingEvents().stream().limit(10).toList();
    List<Event> past = eventService.listPastEvents().stream().limit(10).toList();
    return withLayoutData(
        events
            .data("today", LocalDate.now())
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

  private boolean isMemberOrigin(CommunityContentItem item) {
    if (item == null) {
      return false;
    }
    String id = item.id() == null ? "" : item.id().toLowerCase(Locale.ROOT);
    String source = item.source() == null ? "" : item.source().trim().toLowerCase(Locale.ROOT);
    return id.startsWith("submission-") || "community member".equals(source) || "member".equals(source);
  }

  private int toIntSafely(long value) {
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, value);
  }

  private int starterXpRemaining(
      boolean homeAccountHasGithub, boolean homeAccountHasDiscord, boolean homeStarterHasVote) {
    int total = 0;
    if (!homeAccountHasGithub) {
      total += GamificationActivity.GITHUB_LINKED.xp();
    }
    if (!homeAccountHasDiscord) {
      total += GamificationActivity.DISCORD_LINKED.xp();
    }
    if (!homeStarterHasVote) {
      total += GamificationActivity.COMMUNITY_VOTE.xp();
    }
    return total;
  }

  private List<HomeClassProgress> buildHomeClassProgress(UserProfile profile, int fallbackXp) {
    EnumMap<QuestClass, Integer> xpMap = new EnumMap<>(QuestClass.class);
    if (profile != null && profile.getClassXp() != null) {
      xpMap.putAll(profile.getClassXp());
    }
    int total = xpMap.values().stream().mapToInt(value -> Math.max(0, value)).sum();
    if (total <= 0 && fallbackXp > 0) {
      QuestClass legacyClass =
          profile != null && profile.getDominantQuestClass() != null
              ? profile.getDominantQuestClass()
              : QuestClass.ENGINEER;
      xpMap.put(legacyClass, fallbackXp);
      total = fallbackXp;
    }
    QuestClass dominant =
        xpMap.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
            .max(Map.Entry.<QuestClass, Integer>comparingByValue().thenComparing(entry -> entry.getKey().name()))
            .map(Map.Entry::getKey)
            .orElse(null);
    final int totalXp = total;
    final QuestClass dominantClass = dominant;
    return Arrays.stream(QuestClass.values())
        .map(
            questClass -> {
              int xp = Math.max(0, xpMap.getOrDefault(questClass, 0));
              int sharePercent = totalXp <= 0 ? 0 : (int) Math.round((xp * 100.0d) / totalXp);
              return new HomeClassProgress(
                  questClass.name(),
                  questClass.getDisplayName(),
                  questClass.getEmoji(),
                  xp,
                  sharePercent,
                  questClass == dominantClass && xp > 0);
            })
        .toList();
  }

  private HomeRewardSpotlight chooseRewardSpotlight(
      List<EconomyService.CatalogOffer> offers, long walletBalance) {
    if (offers == null || offers.isEmpty()) {
      return null;
    }
    List<EconomyService.CatalogOffer> withStock =
        offers.stream().filter(offer -> offer.remainingStock() > 0).toList();
    if (withStock.isEmpty()) {
      return null;
    }
    Comparator<EconomyService.CatalogOffer> unlockedComparator =
        Comparator.comparingInt(EconomyService.CatalogOffer::priceHcoin).thenComparing(EconomyService.CatalogOffer::id);
    Optional<EconomyService.CatalogOffer> affordable =
        withStock.stream()
            .filter(offer -> offer.unlocked() && offer.priceHcoin() <= walletBalance)
            .min(unlockedComparator);
    if (affordable.isPresent()) {
      return toRewardSpotlight(affordable.get(), walletBalance);
    }
    Optional<EconomyService.CatalogOffer> unlocked =
        withStock.stream().filter(EconomyService.CatalogOffer::unlocked).min(unlockedComparator);
    if (unlocked.isPresent()) {
      return toRewardSpotlight(unlocked.get(), walletBalance);
    }
    return withStock.stream()
        .filter(offer -> !offer.unlocked())
        .min(
            Comparator.comparingInt(EconomyService.CatalogOffer::requiredLevel)
                .thenComparingInt(EconomyService.CatalogOffer::requiredTotalXp)
                .thenComparingInt(EconomyService.CatalogOffer::requiredClassXp)
                .thenComparingInt(EconomyService.CatalogOffer::priceHcoin))
        .map(offer -> toRewardSpotlight(offer, walletBalance))
        .orElse(null);
  }

  private HomeRewardSpotlight toRewardSpotlight(EconomyService.CatalogOffer offer, long walletBalance) {
    String requiredClassName = null;
    if (offer.requiredClass() != null) {
      QuestClass questClass = QuestClass.fromValue(offer.requiredClass());
      requiredClassName = questClass != null ? questClass.getDisplayName() : offer.requiredClass();
    }
    return new HomeRewardSpotlight(
        offer.id(),
        offer.name(),
        offer.priceHcoin(),
        offer.unlocked(),
        offer.unlocked() && offer.priceHcoin() <= walletBalance,
        Math.max(0, offer.priceHcoin() - (int) Math.min(Integer.MAX_VALUE, walletBalance)),
        offer.requiredLevel(),
        offer.requiredTotalXp(),
        requiredClassName,
        offer.requiredClassXp());
  }

  private HomeNotificationPreview toHomeNotificationPreview(Notification notification) {
    String title =
        notification.title != null && !notification.title.isBlank()
            ? notification.title
            : notification.type != null ? notification.type.name() : "";
    String message = notification.message != null ? notification.message.trim() : "";
    if (message.length() > 120) {
      message = message.substring(0, 117) + "...";
    }
    return new HomeNotificationPreview(title, message);
  }

  private HomeChallengeCard toHomeChallengeCard(
      ChallengeService.ChallengeProgressCard card, HomeChallengesCopy copy) {
    int totalSteps = Math.max(0, card.totalSteps());
    int completedSteps = Math.max(0, Math.min(card.completedSteps(), totalSteps));
    int progressPercent =
        totalSteps <= 0 ? 0 : (int) Math.round((completedSteps * 100.0d) / (double) totalSteps);
    String state =
        card.completed()
            ? "completed"
            : completedSteps > 0 ? "in_progress" : "ready";
    String statusLabel =
        switch (state) {
          case "completed" -> copy.statusCompleted();
          case "in_progress" -> copy.statusInProgress();
          default -> copy.statusReady();
        };
    return new HomeChallengeCard(
        card.id(),
        completedSteps,
        totalSteps,
        Math.max(0, card.rewardHcoin()),
        card.completed(),
        Math.max(0, Math.min(100, progressPercent)),
        state,
        challengeActionUrl(card.id()),
        challengeTitle(card.id(), copy),
        challengeDescription(card.id(), copy),
        challengeCta(card.id(), copy),
        statusLabel,
        formatNamed(copy.rewardHcoinPattern(), "reward", Math.max(0, card.rewardHcoin())),
        formatNamed(
            copy.progressStepsPattern(),
            "completed",
            completedSteps,
            "total",
            totalSteps));
  }

  private HomeChallengeTrend toHomeChallengeTrend(
      ChallengeService.ChallengeTrend trend,
      HomeChallengesCopy challengeCopy,
      HomeChallengeLoopCopy loopCopy) {
    return new HomeChallengeTrend(
        trend.challengeId(),
        challengeTitle(trend.challengeId(), challengeCopy),
        formatNamed(loopCopy.trendingCountPattern(), "count", trend.completions()),
        formatNamed(loopCopy.rewardPattern(), "reward", Math.max(0, trend.rewardHcoin())),
        "/private/profile#challenges-panel");
  }

  private HomeChallengeLeaderboard toHomeChallengeLeaderboard(
      ChallengeService.ChallengeLeaderboard leaderboard, HomeChallengeLoopCopy copy) {
    if (leaderboard == null || leaderboard.rank() <= 0 || leaderboard.completedChallenges() <= 0) {
      return new HomeChallengeLeaderboard(
          0,
          0,
          0,
          null,
          copy.leaderboardEmptyTitle(),
          copy.leaderboardEmptyDesc());
    }
    return new HomeChallengeLeaderboard(
        leaderboard.rank(),
        leaderboard.activeMembers(),
        leaderboard.completedChallenges(),
        formatNamed(copy.leaderboardRankPattern(), "rank", leaderboard.rank()),
        formatNamed(copy.leaderboardSummaryPattern(), "completed", leaderboard.completedChallenges()),
        formatNamed(copy.leaderboardPopulationPattern(), "active", leaderboard.activeMembers()));
  }

  private HomeChallengesCopy homeChallengesCopy(String localeCode) {
    ResourceBundle bundle = localizedChallengeBundle(localeCode);
    return new HomeChallengesCopy(
        bundleText(bundle, "home_challenges_eyebrow"),
        bundleText(bundle, "home_challenges_title"),
        bundleText(bundle, "home_challenges_intro"),
        bundleText(bundle, "home_challenges_cta"),
        bundleText(bundle, "home_challenges_summary_completed"),
        bundleText(bundle, "home_challenges_summary_in_progress"),
        bundleText(bundle, "home_challenges_summary_ready"),
        bundleText(bundle, "challenge_status_completed"),
        bundleText(bundle, "challenge_status_in_progress"),
        bundleText(bundle, "challenge_status_ready"),
        bundleText(bundle, "challenge_reward_hcoin"),
        bundleText(bundle, "challenge_progress_steps"),
        bundleText(bundle, "challenge_community_scout_title"),
        bundleText(bundle, "challenge_community_scout_desc"),
        bundleText(bundle, "challenge_community_scout_cta"),
        bundleText(bundle, "challenge_event_explorer_title"),
        bundleText(bundle, "challenge_event_explorer_desc"),
        bundleText(bundle, "challenge_event_explorer_cta"),
        bundleText(bundle, "challenge_open_source_identity_title"),
        bundleText(bundle, "challenge_open_source_identity_desc"),
        bundleText(bundle, "challenge_open_source_identity_cta"));
  }

  private HomeChallengeLoopCopy homeChallengeLoopCopy(String localeCode) {
    ResourceBundle bundle = localizedChallengeBundle(localeCode);
    return new HomeChallengeLoopCopy(
        bundleText(bundle, "home_p4_trending_eyebrow"),
        bundleText(bundle, "home_p4_trending_title"),
        bundleText(bundle, "home_p4_trending_intro"),
        bundleText(bundle, "home_p4_trending_empty_title"),
        bundleText(bundle, "home_p4_trending_empty_desc"),
        bundleText(bundle, "home_p4_trending_count"),
        bundleText(bundle, "home_p4_trending_cta"),
        bundleText(bundle, "home_p4_leaderboard_eyebrow"),
        bundleText(bundle, "home_p4_leaderboard_title"),
        bundleText(bundle, "home_p4_leaderboard_intro"),
        bundleText(bundle, "home_p4_leaderboard_empty_title"),
        bundleText(bundle, "home_p4_leaderboard_empty_desc"),
        bundleText(bundle, "home_p4_leaderboard_rank"),
        bundleText(bundle, "home_p4_leaderboard_summary"),
        bundleText(bundle, "home_p4_leaderboard_population"),
        bundleText(bundle, "home_p4_leaderboard_cta"),
        bundleText(bundle, "challenge_reward_hcoin"));
  }

  private String bundleText(ResourceBundle bundle, String key) {
    return bundle.containsKey(key) ? bundle.getString(key) : key;
  }

  private ResourceBundle localizedChallengeBundle(String localeCode) {
    String normalized = localeCode == null ? "" : localeCode.trim().toLowerCase(Locale.ROOT);
    Locale bundleLocale =
        normalized.startsWith("es") ? Locale.forLanguageTag("es") : Locale.ROOT;
    return ResourceBundle.getBundle("i18n", bundleLocale);
  }

  private String formatNamed(String pattern, Object... keyValues) {
    String formatted = pattern;
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      String key = String.valueOf(keyValues[i]);
      String value = String.valueOf(keyValues[i + 1]);
      formatted = formatted.replace("{" + key + "}", value);
    }
    return formatted;
  }

  private String challengeTitle(String challengeId, HomeChallengesCopy copy) {
    return switch (challengeId) {
      case "community-scout" -> copy.communityScoutTitle();
      case "event-explorer" -> copy.eventExplorerTitle();
      case "open-source-identity" -> copy.openSourceIdentityTitle();
      default -> challengeId;
    };
  }

  private String challengeDescription(String challengeId, HomeChallengesCopy copy) {
    return switch (challengeId) {
      case "community-scout" -> copy.communityScoutDesc();
      case "event-explorer" -> copy.eventExplorerDesc();
      case "open-source-identity" -> copy.openSourceIdentityDesc();
      default -> challengeId;
    };
  }

  private String challengeCta(String challengeId, HomeChallengesCopy copy) {
    return switch (challengeId) {
      case "community-scout" -> copy.communityScoutCta();
      case "event-explorer" -> copy.eventExplorerCta();
      case "open-source-identity" -> copy.openSourceIdentityCta();
      default -> copy.defaultCta();
    };
  }

  private String challengeActionUrl(String challengeId) {
    if (challengeId == null || challengeId.isBlank()) {
      return "/private/profile#challenges-panel";
    }
    return switch (challengeId) {
      case "community-scout" -> "/comunidad/picks";
      case "event-explorer" -> "/eventos";
      case "open-source-identity" -> "/private/profile#integrations-panel";
      default -> "/private/profile#challenges-panel";
    };
  }

  private record HomeClassProgress(
      String value, String className, String emoji, int xp, int sharePercent, boolean dominant) {}

  private record HomeRewardSpotlight(
      String id,
      String name,
      int priceHcoin,
      boolean unlocked,
      boolean affordable,
      int missingHcoin,
      int requiredLevel,
      int requiredTotalXp,
      String requiredClassName,
      int requiredClassXp) {}

  private record HomeNotificationPreview(String title, String message) {}

  private record HomeChallengesCopy(
      String eyebrow,
      String title,
      String intro,
      String defaultCta,
      String summaryCompleted,
      String summaryInProgress,
      String summaryReady,
      String statusCompleted,
      String statusInProgress,
      String statusReady,
      String rewardHcoinPattern,
      String progressStepsPattern,
      String communityScoutTitle,
      String communityScoutDesc,
      String communityScoutCta,
      String eventExplorerTitle,
      String eventExplorerDesc,
      String eventExplorerCta,
      String openSourceIdentityTitle,
      String openSourceIdentityDesc,
      String openSourceIdentityCta) {}

  private record HomeChallengeLoopCopy(
      String trendingEyebrow,
      String trendingTitle,
      String trendingIntro,
      String trendingEmptyTitle,
      String trendingEmptyDesc,
      String trendingCountPattern,
      String trendingCta,
      String leaderboardEyebrow,
      String leaderboardTitle,
      String leaderboardIntro,
      String leaderboardEmptyTitle,
      String leaderboardEmptyDesc,
      String leaderboardRankPattern,
      String leaderboardSummaryPattern,
      String leaderboardPopulationPattern,
      String leaderboardCta,
      String rewardPattern) {}

  private record HomeChallengeCard(
      String id,
      int completedSteps,
      int totalSteps,
      int rewardHcoin,
      boolean completed,
      int progressPercent,
      String state,
      String actionUrl,
      String title,
      String description,
      String ctaLabel,
      String statusLabel,
      String rewardLabel,
      String progressLabel) {}

  private record HomeChallengeTrend(
      String id, String title, String countLabel, String rewardLabel, String actionUrl) {}

  private record HomeChallengeLeaderboard(
      int rank,
      int activeMembers,
      int completedChallenges,
      String rankLabel,
      String summaryLabel,
      String populationLabel) {}
}
