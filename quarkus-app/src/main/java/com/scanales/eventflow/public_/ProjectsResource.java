package com.scanales.eventflow.public_;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.eventflow.service.GithubService;
import com.scanales.eventflow.service.GithubService.GithubContributor;
import com.scanales.eventflow.service.UsageMetricsService;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Path("/proyectos")
public class ProjectsResource {
  private static final Logger LOG = Logger.getLogger(ProjectsResource.class);
  private static final int MAX_RELEASES = 8;
  private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(6);
  private static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofMinutes(15);

  @Inject UsageMetricsService metrics;
  @Inject SecurityIdentity identity;
  @Inject ObjectMapper mapper;
  @Inject Config config;
  @Inject GithubService githubService;

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
    triggerRefreshAsync(true, "startup");
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

    ProjectSnapshot snapshot = currentSnapshot();
    List<GithubContributor> contributors = githubService.fetchHomeProjectContributors();
    ProjectDashboard dashboard = buildDashboard(snapshot, contributors);
    TemplateInstance template = Templates.proyectos(dashboard);
    return withLayoutData(template, "proyectos");
  }

  private ProjectSnapshot currentSnapshot() {
    triggerRefreshAsync(false, "on-demand");
    return snapshotCache.get();
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
    long durationMs = Duration.between(startedAt, Instant.now()).toMillis();

    ProjectSnapshot previous = snapshotCache.get();
    Instant attemptedAt = Instant.now();

    ProjectRepository resolvedRepository = repository != null ? repository : previous.repository();
    List<ProjectReleaseRaw> resolvedReleases =
        !releases.isEmpty() ? List.copyOf(releases) : previous.releases();
    boolean success = repository != null || !releases.isEmpty();
    Instant loadedAt = success ? attemptedAt : previous.loadedAt();

    ProjectSnapshot updated =
        new ProjectSnapshot(
            resolvedRepository, resolvedReleases, loadedAt, attemptedAt, durationMs, success);
    snapshotCache.set(updated);

    if (success) {
      LOG.infov(
          "Homedir project dashboard cache refreshed reason={0} releases={1} durationMs={2}",
          reason,
          resolvedReleases.size(),
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
      ProjectSnapshot snapshot, List<GithubContributor> contributors) {
    List<ProjectFeature> features = defaultFeatures();
    int liveFeatures = (int) features.stream().filter(feature -> "live".equals(feature.statusClass())).count();
    int betaFeatures = (int) features.stream().filter(feature -> "beta".equals(feature.statusClass())).count();
    int nextFeatures = (int) features.stream().filter(feature -> "next".equals(feature.statusClass())).count();

    int weightedProgress = (liveFeatures * 100) + (betaFeatures * 65) + (nextFeatures * 30);
    int maxProgress = Math.max(1, features.size() * 100);
    int progressPercent = Math.min(100, (int) Math.round((weightedProgress * 100d) / maxProgress));

    List<GithubContributor> topContributors = contributors.stream().limit(8).toList();
    int totalContributions = contributors.stream().mapToInt(GithubContributor::contributions).sum();

    List<ProjectRelease> releaseCards =
        snapshot.releases().stream()
            .map(
                release ->
                    new ProjectRelease(
                        release.tag(),
                        release.label(),
                        release.url(),
                        relativeTime(release.publishedAt()),
                        release.prerelease()))
            .toList();

    String latestRelease = releaseCards.isEmpty() ? "No releases publicadas" : releaseCards.getFirst().label();
    String latestReleaseAgo =
        releaseCards.isEmpty() ? "n/d" : releaseCards.getFirst().publishedAgo();

    List<ProjectHighlight> highlights =
        List.of(
            new ProjectHighlight(
                "Cadencia de releases",
                releaseCards.isEmpty() ? "Sin releases recientes" : releaseCards.size() + " releases recientes",
                latestReleaseAgo + " desde la última publicación"),
            new ProjectHighlight(
                "Salud del backlog",
                snapshot.repository().openIssues() + " issues abiertas",
                "Controlado desde GitHub Issues"),
            new ProjectHighlight(
                "Actividad de código",
                relativeTime(snapshot.repository().lastPushAt()),
                "Último push al repositorio principal"));

    return new ProjectDashboard(
        snapshot.repository(),
        progressPercent,
        liveFeatures,
        betaFeatures,
        nextFeatures,
        contributors.size(),
        totalContributions,
        latestRelease,
        latestReleaseAgo,
        relativeTime(snapshot.repository().lastPushAt()),
        relativeTime(snapshot.loadedAt()),
        releaseCards,
        features,
        topContributors,
        highlights);
  }

  private List<ProjectFeature> defaultFeatures() {
    return List.of(
        new ProjectFeature(
            "Curated Community Feed",
            "Ingesta file-based con ranking, votos 3 estados y destacados semanales.",
            "LIVE",
            "live",
            "/comunidad"),
        new ProjectFeature(
            "Events Persistence",
            "Persistencia de eventos para sobrevivir reinicios y nuevos despliegues.",
            "LIVE",
            "live",
            "/eventos"),
        new ProjectFeature(
            "One-page Home Highlights",
            "Home compacto con Social, Events y Project en una vista rápida.",
            "LIVE",
            "live",
            "/"),
        new ProjectFeature(
            "Global Notifications",
            "Notificaciones globales en tiempo real con WebSocket y centro unificado.",
            "BETA",
            "beta",
            "/notifications/center"),
        new ProjectFeature(
            "Contributor Telemetry Cache",
            "Métricas de contribuidores cacheadas para evitar consultas por request.",
            "BETA",
            "beta",
            "/proyectos"),
        new ProjectFeature(
            "ADev Practitioner Playbook",
            "Formalizar la línea base ADev como guía operativa para próximos ciclos.",
            "NEXT",
            "next",
            "https://github.com/scanalesespinoza/adev"));
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

  private String relativeTime(Instant date) {
    if (date == null) {
      return "n/d";
    }
    long minutes = ChronoUnit.MINUTES.between(date, Instant.now());
    if (minutes < 1) {
      return "ahora";
    }
    if (minutes < 60) {
      return "hace " + minutes + "m";
    }
    long hours = minutes / 60;
    if (hours < 24) {
      return "hace " + hours + "h";
    }
    long days = hours / 24;
    if (days < 30) {
      return "hace " + days + "d";
    }
    long months = days / 30;
    if (months < 12) {
      return "hace " + months + "mo";
    }
    long years = months / 12;
    return "hace " + years + "a";
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
      String latestRelease,
      String latestReleaseAgo,
      String lastPushAgo,
      String snapshotAgo,
      List<ProjectRelease> releases,
      List<ProjectFeature> features,
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

  public record ProjectHighlight(
      String title,
      String value,
      String note) {
  }

  private record ProjectSnapshot(
      ProjectRepository repository,
      List<ProjectReleaseRaw> releases,
      Instant loadedAt,
      Instant lastAttemptAt,
      long loadDurationMs,
      boolean lastAttemptSucceeded) {
    static ProjectSnapshot empty() {
      return new ProjectSnapshot(ProjectRepository.empty(), List.of(), null, null, 0L, false);
    }
  }
}
