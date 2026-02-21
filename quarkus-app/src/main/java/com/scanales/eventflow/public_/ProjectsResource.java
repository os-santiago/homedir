package com.scanales.eventflow.public_;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.eventflow.config.AppMessages;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.service.GithubService;
import com.scanales.eventflow.service.GithubService.GithubContributor;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Path("/proyectos")
public class ProjectsResource {
  private static final Logger LOG = Logger.getLogger(ProjectsResource.class);
  private static final int MAX_RELEASES = 8;
  private static final int MAX_TOP_CONTRIBUTORS = 8;
  private static final int[] ACTIVITY_WINDOWS_MONTHS = new int[] {3, 6, 12};
  private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(6);
  private static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofMinutes(15);
  private static final DateTimeFormatter ISO_INSTANT_UTC = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);
  private static final Pattern LAST_PAGE_LINK_PATTERN =
      Pattern.compile("[?&]page=(\\d+)>;\\s*rel=\\\"last\\\"");

  @Inject UsageMetricsService metrics;
  @Inject GamificationService gamificationService;
  @Inject SecurityIdentity identity;
  @Inject ObjectMapper mapper;
  @Inject Config config;
  @Inject GithubService githubService;
  @Inject AppMessages messages;

  @Inject
  @ConfigProperty(name = "home.project.github.repo-owner", defaultValue = "os-santiago")
  String repoOwner;

  @Inject
  @ConfigProperty(name = "home.project.github.repo-name", defaultValue = "homedir")
  String repoName;

  @Inject
  @ConfigProperty(name = "projects.homedir.cache-ttl", defaultValue = "PT6H")
  Duration cacheTtl;

  @Inject
  @ConfigProperty(name = "projects.homedir.retry-interval", defaultValue = "PT15M")
  Duration retryInterval;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance proyectos(ProjectDashboard dashboard);
  }

  private final HttpClient client = HttpClient.newBuilder().build();
  private final AtomicReference<ProjectSnapshot> snapshotCache =
      new AtomicReference<>(ProjectSnapshot.empty());
  private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
  private final ExecutorService refreshExecutor =
      Executors.newSingleThreadExecutor(
          new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
              Thread t = new Thread(r, "projects-homedir-refresh");
              t.setDaemon(true);
              return t;
            }
          });

  @PostConstruct
  void init() {
    if (!refreshInProgress.compareAndSet(false, true)) {
      return;
    }
    try {
      refreshNow("startup");
    } catch (Exception e) {
      LOG.warn("Homedir project dashboard startup refresh failed; falling back to async refresh", e);
      triggerRefreshAsync(true, "startup-fallback");
    } finally {
      refreshInProgress.set(false);
    }
  }

  @PreDestroy
  void shutdown() {
    refreshExecutor.shutdownNow();
  }

  @Scheduled(every = "{projects.homedir.refresh-interval:6h}")
  void scheduledRefresh() {
    triggerRefreshAsync(true, "schedule");
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance proyectos(
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/proyectos", headers, context);
    currentUserId().ifPresent(userId -> gamificationService.award(userId, GamificationActivity.PROJECT_VIEW));

    ProjectSnapshot snapshot = currentSnapshot();
    List<GithubContributor> contributors = githubService.fetchHomeProjectContributors();
    ProjectDashboard dashboard = buildDashboard(snapshot, contributors, messages);
    TemplateInstance template = Templates.proyectos(dashboard);
    return withLayoutData(template, "proyectos");
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

  private ProjectSnapshot currentSnapshot() {
    ProjectSnapshot snapshot = snapshotCache.get();
    if (isColdStartSnapshot(snapshot) && refreshInProgress.compareAndSet(false, true)) {
      try {
        refreshNow("on-demand-cold-start");
      } catch (Exception e) {
        LOG.warn("Homedir project dashboard cold-start refresh failed", e);
      } finally {
        refreshInProgress.set(false);
      }
      return snapshotCache.get();
    }
    triggerRefreshAsync(false, "on-demand");
    return snapshot;
  }

  private void triggerRefreshAsync(boolean force, String reason) {
    ProjectSnapshot snapshot = snapshotCache.get();
    Instant now = Instant.now();
    if (!force && !shouldRefresh(snapshot, now)) {
      return;
    }
    if (!refreshInProgress.compareAndSet(false, true)) {
      return;
    }
    refreshExecutor.submit(
        () -> {
          try {
            refreshNow(reason);
          } finally {
            refreshInProgress.set(false);
          }
        });
  }

  private boolean shouldRefresh(ProjectSnapshot snapshot, Instant now) {
    if (snapshot.loadedAt() != null && now.isBefore(snapshot.loadedAt().plus(effectiveCacheTtl()))) {
      return false;
    }
    if (snapshot.lastAttemptAt() == null) {
      return true;
    }
    return now.isAfter(snapshot.lastAttemptAt().plus(effectiveRetryInterval()));
  }

  private boolean isColdStartSnapshot(ProjectSnapshot snapshot) {
    if (snapshot == null) {
      return true;
    }
    boolean noCoreData =
        snapshot.releases().isEmpty()
            && snapshot.repository().stars() == 0
            && snapshot.repository().forks() == 0;
    return snapshot.loadedAt() == null && noCoreData && !snapshot.activity().hasData();
  }

  private Duration effectiveCacheTtl() {
    if (cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()) {
      return DEFAULT_CACHE_TTL;
    }
    return cacheTtl;
  }

  private Duration effectiveRetryInterval() {
    if (retryInterval == null || retryInterval.isNegative() || retryInterval.isZero()) {
      return DEFAULT_RETRY_INTERVAL;
    }
    return retryInterval;
  }

  private void refreshNow(String reason) {
    Instant startedAt = Instant.now();
    ProjectRepository repository = loadRepository();
    List<ProjectReleaseRaw> releases = loadReleases();
    ProjectActivity activity = loadActivity();
    long durationMs = Duration.between(startedAt, Instant.now()).toMillis();

    ProjectSnapshot previous = snapshotCache.get();
    Instant attemptedAt = Instant.now();

    ProjectRepository resolvedRepository = repository != null ? repository : previous.repository();
    List<ProjectReleaseRaw> resolvedReleases =
        !releases.isEmpty() ? List.copyOf(releases) : previous.releases();
    ProjectActivity resolvedActivity = activity != null && activity.hasData() ? activity : previous.activity();
    boolean coreDataReady = repository != null || !releases.isEmpty();
    boolean activityDataReady = resolvedActivity.hasData();
    boolean success = coreDataReady || activityDataReady;
    Instant loadedAt = success ? attemptedAt : previous.loadedAt();

    ProjectSnapshot updated =
        new ProjectSnapshot(
            resolvedRepository,
            resolvedReleases,
            resolvedActivity,
            loadedAt,
            attemptedAt,
            durationMs,
            success);
    snapshotCache.set(updated);

    if (success) {
      LOG.infov(
          "Homedir project dashboard cache refreshed reason={0} releases={1} activityReady={2} durationMs={3}",
          reason,
          resolvedReleases.size(),
          activityDataReady,
          durationMs);
    } else {
      LOG.warnv(
          "Homedir project dashboard refresh failed reason={0}; keeping previous snapshot", reason);
    }
  }

  private ProjectRepository loadRepository() {
    try {
      HttpRequest request =
          githubRequestBuilder(URI.create("https://api.github.com/repos/" + repoOwner + "/" + repoName))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        LOG.warnf(
            "Homedir repository fetch failed status=%d body=%s", response.statusCode(), response.body());
        return null;
      }
      JsonNode node = mapper.readTree(response.body());
      return new ProjectRepository(
          node.path("name").asText(repoName),
          node.path("description").asText("Open source platform for OSS Santiago community operations."),
          node.path("html_url").asText("https://github.com/" + repoOwner + "/" + repoName),
          node.path("stargazers_count").asInt(0),
          node.path("forks_count").asInt(0),
          node.path("open_issues_count").asInt(0),
          node.path("subscribers_count").asInt(node.path("watchers_count").asInt(0)),
          parseInstant(node.path("pushed_at").asText(null)));
    } catch (Exception e) {
      LOG.warn("Homedir repository fetch failed", e);
      return null;
    }
  }

  private List<ProjectReleaseRaw> loadReleases() {
    try {
      HttpRequest request =
          githubRequestBuilder(
                  URI.create(
                      "https://api.github.com/repos/"
                          + repoOwner
                          + "/"
                          + repoName
                          + "/releases?per_page="
                          + MAX_RELEASES))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        LOG.warnf("Homedir releases fetch failed status=%d body=%s", response.statusCode(), response.body());
        return List.of();
      }
      JsonNode root = mapper.readTree(response.body());
      if (!root.isArray()) {
        return List.of();
      }
      List<ProjectReleaseRaw> releases = new ArrayList<>();
      for (JsonNode node : root) {
        if (node.path("draft").asBoolean(false)) {
          continue;
        }
        String tag = node.path("tag_name").asText("");
        if (tag.isBlank()) {
          continue;
        }
        String name = node.path("name").asText("").trim();
        String label = name.isBlank() ? tag : name;
        releases.add(
            new ProjectReleaseRaw(
                tag,
                label,
                node.path("html_url")
                    .asText("https://github.com/" + repoOwner + "/" + repoName + "/releases"),
                parseInstant(node.path("published_at").asText(null)),
                node.path("prerelease").asBoolean(false)));
      }
      return releases;
    } catch (Exception e) {
      LOG.warn("Homedir releases fetch failed", e);
      return List.of();
    }
  }

  private ProjectActivity loadActivity() {
    try {
      HttpRequest request =
          githubRequestBuilder(
                  URI.create(
                      "https://api.github.com/repos/" + repoOwner + "/" + repoName + "/stats/contributors"))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 202) {
        LOG.infov("Homedir activity stats pending generation in GitHub cache");
        return loadActivityFallbackFromCommitsApi();
      }
      if (response.statusCode() >= 400) {
        LOG.warnf("Homedir activity fetch failed status=%d body=%s", response.statusCode(), response.body());
        return loadActivityFallbackFromCommitsApi();
      }
      JsonNode root = mapper.readTree(response.body());
      if (!root.isArray()) {
        return loadActivityFallbackFromCommitsApi();
      }

      List<WeeklyActivity> weekly = new ArrayList<>();
      long totalCommits = 0L;
      long totalAdditions = 0L;
      long totalDeletions = 0L;

      for (JsonNode contributor : root) {
        JsonNode weeks = contributor.path("weeks");
        if (!weeks.isArray()) {
          continue;
        }
        for (JsonNode week : weeks) {
          long epochWeek = week.path("w").asLong(0L);
          if (epochWeek <= 0L) {
            continue;
          }
          long commits = Math.max(0L, week.path("c").asLong(0L));
          long additions = Math.max(0L, week.path("a").asLong(0L));
          long deletions = Math.abs(week.path("d").asLong(0L));
          totalCommits += commits;
          totalAdditions += additions;
          totalDeletions += deletions;
          weekly.add(
              new WeeklyActivity(
                  Instant.ofEpochSecond(epochWeek), commits, additions, deletions, additions + deletions));
        }
      }

      if (weekly.isEmpty() && totalCommits == 0L && totalAdditions == 0L && totalDeletions == 0L) {
        return loadActivityFallbackFromCommitsApi();
      }

      Instant now = Instant.now();
      List<ProjectWindowActivity> windows =
          List.of(
              summarizeWindow(weekly, 3, now),
              summarizeWindow(weekly, 6, now),
              summarizeWindow(weekly, 12, now));
      ProjectWindowActivity window12 = windows.get(2);
      long yearlyMonthlyAverage = window12.averageCommitsPerMonth();
      long linesChanged = totalAdditions + totalDeletions;
      long netLines = Math.max(0L, totalAdditions - totalDeletions);

      return new ProjectActivity(
          totalCommits,
          totalAdditions,
          totalDeletions,
          linesChanged,
          netLines,
          windows,
          yearlyMonthlyAverage);
    } catch (Exception e) {
      LOG.warn("Homedir activity fetch failed", e);
      return loadActivityFallbackFromCommitsApi();
    }
  }

  private ProjectActivity loadActivityFallbackFromCommitsApi() {
    try {
      Instant now = Instant.now();
      long commitsTotal = countCommits(null);
      long commits3 = countCommits(now.minus(90, ChronoUnit.DAYS));
      long commits6 = countCommits(now.minus(180, ChronoUnit.DAYS));
      long commits12 = countCommits(now.minus(365, ChronoUnit.DAYS));
      CodeFrequencySummary codeSummary = loadCodeFrequencySummary(now);

      if (commitsTotal <= 0L && commits12 <= 0L && (codeSummary == null || codeSummary.totalLinesChanged() <= 0L)) {
        return null;
      }

      Map<Integer, WindowLines> linesByWindow =
          codeSummary != null ? codeSummary.byMonths() : Map.of();
      List<ProjectWindowActivity> windows = new ArrayList<>();
      windows.add(windowWithFallback(3, commits3, linesByWindow.get(3)));
      windows.add(windowWithFallback(6, commits6, linesByWindow.get(6)));
      windows.add(windowWithFallback(12, commits12, linesByWindow.get(12)));

      long additions = codeSummary != null ? codeSummary.totalAdditions() : 0L;
      long deletions = codeSummary != null ? codeSummary.totalDeletions() : 0L;
      long linesChanged = codeSummary != null ? codeSummary.totalLinesChanged() : 0L;
      long netLines = codeSummary != null ? codeSummary.netLines() : 0L;
      long yearlyMonthlyAverage = Math.max(0L, Math.round(commits12 / 12d));

      LOG.infov(
          "Homedir activity fallback used commitsTotal={0} commits12={1} linesChanged={2}",
          commitsTotal,
          commits12,
          linesChanged);

      return new ProjectActivity(
          commitsTotal,
          additions,
          deletions,
          linesChanged,
          netLines,
          List.copyOf(windows),
          yearlyMonthlyAverage);
    } catch (Exception e) {
      LOG.warn("Homedir activity fallback fetch failed", e);
      return null;
    }
  }

  private ProjectWindowActivity windowWithFallback(int months, long commits, WindowLines lines) {
    long linesChanged = lines != null ? lines.linesChanged() : 0L;
    long netLines = lines != null ? lines.netLines() : 0L;
    long avgCommitsPerMonth = Math.max(0L, Math.round(commits / (double) months));
    return new ProjectWindowActivity(months, commits, linesChanged, Math.max(0L, netLines), avgCommitsPerMonth);
  }

  private long countCommits(Instant since) {
    try {
      StringBuilder url = new StringBuilder("https://api.github.com/repos/");
      url.append(repoOwner).append("/").append(repoName).append("/commits?per_page=1");
      if (since != null) {
        url.append("&since=").append(ISO_INSTANT_UTC.format(since));
      }
      HttpRequest request = githubRequestBuilder(URI.create(url.toString())).GET().build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        LOG.warnf("Homedir commit count fetch failed status=%d body=%s", response.statusCode(), response.body());
        return 0L;
      }
      JsonNode root = mapper.readTree(response.body());
      if (!root.isArray() || root.isEmpty()) {
        return 0L;
      }
      String link = response.headers().firstValue("Link").orElse("");
      Matcher matcher = LAST_PAGE_LINK_PATTERN.matcher(link);
      if (matcher.find()) {
        try {
          return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
          return 1L;
        }
      }
      return root.size();
    } catch (Exception e) {
      LOG.warn("Homedir commit count fallback failed", e);
      return 0L;
    }
  }

  private CodeFrequencySummary loadCodeFrequencySummary(Instant now) {
    try {
      HttpRequest request =
          githubRequestBuilder(
                  URI.create(
                      "https://api.github.com/repos/" + repoOwner + "/" + repoName + "/stats/code_frequency"))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 202) {
        return null;
      }
      if (response.statusCode() >= 400) {
        LOG.warnf(
            "Homedir code frequency fetch failed status=%d body=%s", response.statusCode(), response.body());
        return null;
      }
      JsonNode root = mapper.readTree(response.body());
      if (!root.isArray()) {
        return null;
      }

      long totalAdditions = 0L;
      long totalDeletions = 0L;
      long w3Add = 0L;
      long w3Del = 0L;
      long w6Add = 0L;
      long w6Del = 0L;
      long w12Add = 0L;
      long w12Del = 0L;

      Instant from3 = now.minus(90, ChronoUnit.DAYS);
      Instant from6 = now.minus(180, ChronoUnit.DAYS);
      Instant from12 = now.minus(365, ChronoUnit.DAYS);

      for (JsonNode row : root) {
        if (!row.isArray() || row.size() < 3) {
          continue;
        }
        long epochWeek = row.get(0).asLong(0L);
        if (epochWeek <= 0L) {
          continue;
        }
        long additions = Math.max(0L, row.get(1).asLong(0L));
        long deletions = Math.abs(row.get(2).asLong(0L));
        Instant week = Instant.ofEpochSecond(epochWeek);

        totalAdditions += additions;
        totalDeletions += deletions;

        if (!week.isBefore(from12)) {
          w12Add += additions;
          w12Del += deletions;
        }
        if (!week.isBefore(from6)) {
          w6Add += additions;
          w6Del += deletions;
        }
        if (!week.isBefore(from3)) {
          w3Add += additions;
          w3Del += deletions;
        }
      }

      return new CodeFrequencySummary(
          totalAdditions,
          totalDeletions,
          Map.of(
              3, new WindowLines(w3Add + w3Del, Math.max(0L, w3Add - w3Del)),
              6, new WindowLines(w6Add + w6Del, Math.max(0L, w6Add - w6Del)),
              12, new WindowLines(w12Add + w12Del, Math.max(0L, w12Add - w12Del))));
    } catch (Exception e) {
      LOG.warn("Homedir code frequency fallback failed", e);
      return null;
    }
  }

  private ProjectWindowActivity summarizeWindow(List<WeeklyActivity> weekly, int months, Instant now) {
    Instant from = now.minus(months * 30L, ChronoUnit.DAYS);
    long commits = 0L;
    long linesChanged = 0L;
    long netLines = 0L;
    for (WeeklyActivity point : weekly) {
      if (point.weekStart().isBefore(from)) {
        continue;
      }
      commits += point.commits();
      linesChanged += point.linesChanged();
      netLines += point.additions() - point.deletions();
    }
    long avgCommitsPerMonth = Math.max(0L, Math.round(commits / (double) months));
    return new ProjectWindowActivity(months, commits, linesChanged, Math.max(0L, netLines), avgCommitsPerMonth);
  }

  private HttpRequest.Builder githubRequestBuilder(URI uri) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "homedir-project-dashboard");
    String token = config.getOptionalValue("GH_TOKEN", String.class).orElse("").trim();
    if (!token.isBlank()) {
      builder.header("Authorization", "Bearer " + token);
    }
    return builder;
  }

  private ProjectDashboard buildDashboard(
      ProjectSnapshot snapshot, List<GithubContributor> contributors, AppMessages i18n) {
    List<ProjectFeature> features = defaultFeatures(i18n);
    List<ProjectCapability> capabilities = defaultCapabilities(i18n);
    int liveFeatures = (int) features.stream().filter(feature -> "live".equals(feature.statusClass())).count();
    int betaFeatures = (int) features.stream().filter(feature -> "beta".equals(feature.statusClass())).count();
    int nextFeatures = (int) features.stream().filter(feature -> "next".equals(feature.statusClass())).count();

    int weightedProgress = (liveFeatures * 100) + (betaFeatures * 65) + (nextFeatures * 30);
    int maxProgress = Math.max(1, features.size() * 100);
    int progressPercent = Math.min(100, (int) Math.round((weightedProgress * 100d) / maxProgress));

    List<GithubContributor> topContributors = contributors.stream().limit(MAX_TOP_CONTRIBUTORS).toList();
    int totalContributions = contributors.stream().mapToInt(GithubContributor::contributions).sum();

    List<ProjectRelease> releaseCards =
        snapshot.releases().stream()
            .map(
                release ->
                    new ProjectRelease(
                        release.tag(),
                        release.label(),
                        release.url(),
                        relativeTime(release.publishedAt(), i18n),
                        release.prerelease()))
            .toList();

    String latestRelease =
        releaseCards.isEmpty() ? i18n.project_dashboard_latest_release_none() : releaseCards.getFirst().label();
    String latestReleaseAgo =
        releaseCards.isEmpty() ? i18n.project_dashboard_relative_na() : releaseCards.getFirst().publishedAgo();
    Instant latestReleaseAt =
        snapshot.releases().isEmpty() ? null : snapshot.releases().getFirst().publishedAt();
    Instant roadmapSignalAt = latestOf(snapshot.repository().lastPushAt(), latestReleaseAt);
    ProjectActivity activity = snapshot.activity();
    List<ProjectWindowActivity> velocityWindows = activity.windowSummaries();

    List<ProjectHighlight> highlights =
        List.of(
            new ProjectHighlight(
                i18n.project_dashboard_highlight_release_title(),
                releaseCards.isEmpty()
                    ? i18n.project_dashboard_highlight_release_none()
                    : i18n.project_dashboard_highlight_release_count(releaseCards.size()),
                i18n.project_dashboard_highlight_release_note(latestReleaseAgo)),
            new ProjectHighlight(
                i18n.project_dashboard_highlight_backlog_title(),
                i18n.project_dashboard_highlight_backlog_open_issues(snapshot.repository().openIssues()),
                i18n.project_dashboard_highlight_backlog_note()),
            new ProjectHighlight(
                i18n.project_dashboard_highlight_activity_title(),
                relativeTime(snapshot.repository().lastPushAt(), i18n),
                i18n.project_dashboard_highlight_activity_note()));

    return new ProjectDashboard(
        snapshot.repository(),
        progressPercent,
        liveFeatures,
        betaFeatures,
        nextFeatures,
        contributors.size(),
        totalContributions,
        formatCompact(activity.totalCommits()),
        formatCompact(activity.netLines()),
        activity.yearlyAverageCommitsPerMonth(),
        latestRelease,
        latestReleaseAgo,
        relativeTime(snapshot.repository().lastPushAt(), i18n),
        relativeTime(roadmapSignalAt, i18n),
        relativeTime(snapshot.loadedAt(), i18n),
        releaseCards,
        features,
        capabilities,
        velocityWindows,
        topContributors,
        highlights);
  }

  private List<ProjectFeature> defaultFeatures(AppMessages i18n) {
    return List.of(
        new ProjectFeature(
            i18n.project_dashboard_feature_community_feed_title(),
            i18n.project_dashboard_feature_community_feed_desc(),
            i18n.project_dashboard_status_live(),
            "live",
            "/comunidad"),
        new ProjectFeature(
            i18n.project_dashboard_feature_gamification_title(),
            i18n.project_dashboard_feature_gamification_desc(),
            i18n.project_dashboard_status_live(),
            "live",
            "/private/profile"),
        new ProjectFeature(
            i18n.project_dashboard_feature_rewards_catalog_title(),
            i18n.project_dashboard_feature_rewards_catalog_desc(),
            i18n.project_dashboard_status_live(),
            "live",
            "/private/profile/catalog"),
        new ProjectFeature(
            i18n.project_dashboard_feature_home_highlights_title(),
            i18n.project_dashboard_feature_home_highlights_desc(),
            i18n.project_dashboard_status_live(),
            "live",
            "/"),
        new ProjectFeature(
            i18n.project_dashboard_feature_events_persistence_title(),
            i18n.project_dashboard_feature_events_persistence_desc(),
            i18n.project_dashboard_status_live(),
            "live",
            "/eventos"),
        new ProjectFeature(
            i18n.project_dashboard_feature_global_notifications_title(),
            i18n.project_dashboard_feature_global_notifications_desc(),
            i18n.project_dashboard_status_live(),
            "live",
            "/notifications"),
        new ProjectFeature(
            i18n.project_dashboard_feature_contributor_cache_title(),
            i18n.project_dashboard_feature_contributor_cache_desc(),
            i18n.project_dashboard_status_beta(),
            "beta",
            "/proyectos"),
        new ProjectFeature(
            i18n.project_dashboard_feature_adev_playbook_title(),
            i18n.project_dashboard_feature_adev_playbook_desc(),
            i18n.project_dashboard_status_next(),
            "next",
            "https://github.com/scanalesespinoza/adev"));
  }

  private List<ProjectCapability> defaultCapabilities(AppMessages i18n) {
    return List.of(
        new ProjectCapability(
            i18n.project_dashboard_capability_platform_title(),
            i18n.project_dashboard_capability_platform_desc(),
            List.of(
                i18n.project_dashboard_capability_platform_bullet_1(),
                i18n.project_dashboard_capability_platform_bullet_2(),
                i18n.project_dashboard_capability_platform_bullet_3()),
            "/eventos"),
        new ProjectCapability(
            i18n.project_dashboard_capability_community_title(),
            i18n.project_dashboard_capability_community_desc(),
            List.of(
                i18n.project_dashboard_capability_community_bullet_1(),
                i18n.project_dashboard_capability_community_bullet_2(),
                i18n.project_dashboard_capability_community_bullet_3()),
            "/comunidad"),
        new ProjectCapability(
            i18n.project_dashboard_capability_reliability_title(),
            i18n.project_dashboard_capability_reliability_desc(),
            List.of(
                i18n.project_dashboard_capability_reliability_bullet_1(),
                i18n.project_dashboard_capability_reliability_bullet_2(),
                i18n.project_dashboard_capability_reliability_bullet_3()),
            "/proyectos"),
        new ProjectCapability(
            i18n.project_dashboard_capability_delivery_title(),
            i18n.project_dashboard_capability_delivery_desc(),
            List.of(
                i18n.project_dashboard_capability_delivery_bullet_1(),
                i18n.project_dashboard_capability_delivery_bullet_2(),
                i18n.project_dashboard_capability_delivery_bullet_3()),
            "https://github.com/os-santiago/homedir/releases"));
  }

  private Instant latestOf(Instant left, Instant right) {
    if (left == null) {
      return right;
    }
    if (right == null) {
      return left;
    }
    return left.isAfter(right) ? left : right;
  }

  private String formatCompact(long value) {
    if (value >= 1_000_000L) {
      return String.format(Locale.US, "%.1fM", value / 1_000_000d);
    }
    if (value >= 1_000L) {
      return String.format(Locale.US, "%.1fk", value / 1_000d);
    }
    return Long.toString(value);
  }

  private Instant parseInstant(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw);
    } catch (Exception e) {
      return null;
    }
  }

  private String relativeTime(Instant date, AppMessages i18n) {
    if (date == null) {
      return i18n.project_dashboard_relative_na();
    }
    long minutes = ChronoUnit.MINUTES.between(date, Instant.now());
    if (minutes < 1) {
      return i18n.project_dashboard_relative_now();
    }
    if (minutes < 60) {
      return i18n.project_dashboard_relative_minutes(minutes);
    }
    long hours = minutes / 60;
    if (hours < 24) {
      return i18n.project_dashboard_relative_hours(hours);
    }
    long days = hours / 24;
    if (days < 30) {
      return i18n.project_dashboard_relative_days(days);
    }
    long months = days / 30;
    if (months < 12) {
      return i18n.project_dashboard_relative_months(months);
    }
    long years = months / 12;
    return i18n.project_dashboard_relative_years(years);
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

  public record ProjectDashboard(
      ProjectRepository repository,
      int progressPercent,
      int liveFeatures,
      int betaFeatures,
      int nextFeatures,
      int contributorCount,
      int totalContributions,
      String totalCommits,
      String netLines,
      long yearlyAverageCommitsPerMonth,
      String latestRelease,
      String latestReleaseAgo,
      String lastPushAgo,
      String roadmapUpdatedAgo,
      String snapshotAgo,
      List<ProjectRelease> releases,
      List<ProjectFeature> features,
      List<ProjectCapability> capabilities,
      List<ProjectWindowActivity> velocityWindows,
      List<GithubContributor> topContributors,
      List<ProjectHighlight> highlights) {
  }

  public record ProjectRepository(
      String name,
      String description,
      String htmlUrl,
      int stars,
      int forks,
      int openIssues,
      int watchers,
      Instant lastPushAt) {
    static ProjectRepository empty() {
      return new ProjectRepository(
          "homedir",
          "Open source platform for OSS Santiago community operations.",
          "https://github.com/os-santiago/homedir",
          0,
          0,
          0,
          0,
          null);
    }
  }

  public record ProjectRelease(
      String tag,
      String label,
      String url,
      String publishedAgo,
      boolean prerelease) {
  }

  public record ProjectReleaseRaw(
      String tag,
      String label,
      String url,
      Instant publishedAt,
      boolean prerelease) {
  }

  public record ProjectFeature(
      String title,
      String description,
      String statusLabel,
      String statusClass,
      String href) {
  }

  public record ProjectCapability(
      String title,
      String description,
      List<String> bullets,
      String href) {
  }

  public record ProjectWindowActivity(
      int months,
      long commits,
      long linesChanged,
      long netLines,
      long averageCommitsPerMonth) {
  }

  private record WindowLines(long linesChanged, long netLines) {
  }

  private record CodeFrequencySummary(
      long totalAdditions,
      long totalDeletions,
      Map<Integer, WindowLines> byMonths) {
    long totalLinesChanged() {
      return totalAdditions + totalDeletions;
    }

    long netLines() {
      return Math.max(0L, totalAdditions - totalDeletions);
    }
  }

  private record WeeklyActivity(
      Instant weekStart,
      long commits,
      long additions,
      long deletions,
      long linesChanged) {
  }

  private record ProjectActivity(
      long totalCommits,
      long totalAdditions,
      long totalDeletions,
      long totalLinesChanged,
      long netLines,
      List<ProjectWindowActivity> windowSummaries,
      long yearlyAverageCommitsPerMonth) {

    boolean hasData() {
      return totalCommits > 0L || totalLinesChanged > 0L;
    }

    static ProjectActivity empty() {
      return new ProjectActivity(0L, 0L, 0L, 0L, 0L, defaultWindows(), 0L);
    }

    private static List<ProjectWindowActivity> defaultWindows() {
      List<ProjectWindowActivity> empty = new ArrayList<>();
      for (int months : ACTIVITY_WINDOWS_MONTHS) {
        empty.add(new ProjectWindowActivity(months, 0L, 0L, 0L, 0L));
      }
      return List.copyOf(empty);
    }
  }

  public record ProjectHighlight(
      String title,
      String value,
      String note) {
  }

  private record ProjectSnapshot(
      ProjectRepository repository,
      List<ProjectReleaseRaw> releases,
      ProjectActivity activity,
      Instant loadedAt,
      Instant lastAttemptAt,
      long loadDurationMs,
      boolean lastAttemptSucceeded) {
    static ProjectSnapshot empty() {
      return new ProjectSnapshot(ProjectRepository.empty(), List.of(), ProjectActivity.empty(), null, null, 0L, false);
    }
  }
}
