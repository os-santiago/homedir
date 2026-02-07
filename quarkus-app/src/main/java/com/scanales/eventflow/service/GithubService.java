package com.scanales.eventflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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

@ApplicationScoped
public class GithubService {

  private static final Logger LOG = Logger.getLogger(GithubService.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration DEFAULT_CONTRIBUTORS_CACHE_TTL = Duration.ofHours(24);
  private static final int MAX_CONTRIBUTORS = 10;

  @Inject
  ObjectMapper objectMapper;

  @Inject
  Config config;

  @Inject
  @ConfigProperty(name = "home.project.github.repo-owner", defaultValue = "os-santiago")
  String homeProjectRepoOwner;

  @Inject
  @ConfigProperty(name = "home.project.github.repo-name", defaultValue = "homedir")
  String homeProjectRepoName;

  @Inject
  @ConfigProperty(name = "home.project.github.cache-ttl", defaultValue = "PT24H")
  Duration contributorsCacheTtl;

  @Inject
  @ConfigProperty(name = "home.project.github.retry-interval", defaultValue = "PT15M")
  Duration contributorsRetryInterval;

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(REQUEST_TIMEOUT)
      .build();
  private final AtomicReference<ContributorsCacheSnapshot> contributorsCache =
      new AtomicReference<>(ContributorsCacheSnapshot.empty());
  private final AtomicBoolean contributorsRefreshInProgress = new AtomicBoolean(false);
  private final ExecutorService contributorsRefreshExecutor =
      Executors.newSingleThreadExecutor(
          new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
              Thread t = new Thread(r, "github-contributors-refresh");
              t.setDaemon(true);
              return t;
            }
          });

  @PostConstruct
  void initContributorsCache() {
    triggerContributorsRefreshAsync(true, "startup");
  }

  @PreDestroy
  void shutdown() {
    contributorsRefreshExecutor.shutdownNow();
  }

  @Scheduled(every = "{home.project.github.refresh-interval:24h}")
  void scheduledContributorsRefresh() {
    triggerContributorsRefreshAsync(true, "schedule");
  }

  public List<GithubContributor> fetchHomeProjectContributors() {
    maybeRefreshContributorsOnDemand();
    return contributorsCache.get().contributors();
  }

  public String exchangeCode(String code) throws IOException, InterruptedException {
    HttpRequest tokenRequest = HttpRequest.newBuilder()
        .uri(URI.create("https://github.com/login/oauth/access_token"))
        .timeout(REQUEST_TIMEOUT)
        .header("Accept", "application/json")
        .POST(
            HttpRequest.BodyPublishers.ofString(
                "client_id="
                    + url(getGithubClientId())
                    + "&client_secret="
                    + url(getGithubClientSecret())
                    + "&code="
                    + url(code)))
        .build();
    HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
    if (tokenResponse.statusCode() >= 400) {
      LOG.warnf("GitHub token exchange failed: %s", tokenResponse.body());
      throw new IOException("GitHub token exchange failed");
    }
    JsonNode tokenJson = objectMapper.readTree(tokenResponse.body());
    String accessToken = tokenJson.path("access_token").asText();
    if (accessToken == null || accessToken.isBlank()) {
      LOG.warnf("GitHub token missing access_token field: %s", tokenResponse.body());
      throw new IOException("GitHub token missing access_token");
    }
    return accessToken;
  }

  public GithubProfile fetchUser(String accessToken) throws IOException, InterruptedException {
    HttpRequest meRequest = HttpRequest.newBuilder()
        .uri(URI.create("https://api.github.com/user"))
        .timeout(REQUEST_TIMEOUT)
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + accessToken)
        .build();
    HttpResponse<String> meResponse = httpClient.send(meRequest, HttpResponse.BodyHandlers.ofString());
    if (meResponse.statusCode() >= 400) {
      LOG.warnf("GitHub user fetch failed: %s", meResponse.body());
      throw new IOException("GitHub user fetch failed");
    }
    JsonNode userJson = objectMapper.readTree(meResponse.body());
    String login = userJson.path("login").asText(null);
    if (login == null || login.isBlank()) {
      throw new IOException("GitHub user missing login");
    }
    return new GithubProfile(
        login,
        userJson.path("html_url").asText(),
        userJson.path("avatar_url").asText(),
        userJson.path("id").asText(),
        userJson.path("email").asText(null));
  }

  public List<GithubContributor> fetchContributors(String owner, String repo) {
    return loadContributorsFromGithub(owner, repo);
  }

  List<GithubContributor> loadContributorsFromGithub(String owner, String repo) {
    if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
      return List.of();
    }
    try {
      String githubToken = getGithubApiToken();
      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
          .uri(
              URI.create(
                  "https://api.github.com/repos/"
                      + owner
                      + "/"
                      + repo
                      + "/contributors?per_page="
                      + MAX_CONTRIBUTORS))
          .timeout(REQUEST_TIMEOUT)
          .header("Accept", "application/vnd.github+json")
          .header("X-GitHub-Api-Version", "2022-11-28")
          .header("User-Agent", "homedir-service");
      if (!githubToken.isBlank()) {
        requestBuilder.header("Authorization", "Bearer " + githubToken);
      }
      HttpRequest request = requestBuilder.build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        LOG.warnf("GitHub contributors fetch failed status=%d body=%s", response.statusCode(), response.body());
        return List.of();
      }

      JsonNode json = objectMapper.readTree(response.body());
      if (!json.isArray()) {
        return List.of();
      }
      List<GithubContributor> contributors = new ArrayList<>();
      for (JsonNode node : json) {
        String login = node.path("login").asText("");
        if (login.isBlank()) {
          continue;
        }
        contributors.add(new GithubContributor(
            login,
            node.path("avatar_url").asText(),
            node.path("html_url").asText(),
            node.path("contributions").asInt()));
      }
      return contributors;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("GitHub contributors fetch interrupted", e);
      return List.of();
    } catch (Exception e) {
      LOG.warn("GitHub contributors fetch failed", e);
      return List.of();
    }
  }

  void refreshHomeProjectContributorsNowForTests() {
    refreshHomeProjectContributors("test");
  }

  private void maybeRefreshContributorsOnDemand() {
    triggerContributorsRefreshAsync(false, "on-demand");
  }

  private void triggerContributorsRefreshAsync(boolean force, String reason) {
    ContributorsCacheSnapshot snapshot = contributorsCache.get();
    Instant now = Instant.now();
    if (!force && !shouldRefresh(snapshot, now)) {
      return;
    }
    if (!contributorsRefreshInProgress.compareAndSet(false, true)) {
      return;
    }
    contributorsRefreshExecutor.submit(
        () -> {
          try {
            refreshHomeProjectContributors(reason);
          } finally {
            contributorsRefreshInProgress.set(false);
          }
        });
  }

  private void refreshHomeProjectContributors(String reason) {
    Instant startedAt = Instant.now();
    List<GithubContributor> loaded = loadContributorsFromGithub(homeProjectRepoOwner, homeProjectRepoName);
    long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
    ContributorsCacheSnapshot previous = contributorsCache.get();
    Instant attemptedAt = Instant.now();

    if (!loaded.isEmpty()) {
      contributorsCache.set(
          new ContributorsCacheSnapshot(
              List.copyOf(loaded),
              attemptedAt,
              attemptedAt,
              durationMs,
              true));
      LOG.infov(
          "GitHub contributors cache refreshed reason={0} contributors={1} durationMs={2}",
          reason,
          loaded.size(),
          durationMs);
      return;
    }

    if (!previous.contributors().isEmpty()) {
      contributorsCache.set(
          new ContributorsCacheSnapshot(
              previous.contributors(),
              previous.loadedAt(),
              attemptedAt,
              durationMs,
              false));
      LOG.warnv(
          "GitHub contributors refresh returned empty reason={0}; keeping cached contributors={1}",
          reason,
          previous.contributors().size());
      return;
    }

    contributorsCache.set(
        new ContributorsCacheSnapshot(
            List.of(),
            previous.loadedAt(),
            attemptedAt,
            durationMs,
            false));
    LOG.warnv("GitHub contributors cache is empty after refresh reason={0}", reason);
  }

  private Duration effectiveCacheTtl() {
    if (contributorsCacheTtl == null || contributorsCacheTtl.isNegative() || contributorsCacheTtl.isZero()) {
      return DEFAULT_CONTRIBUTORS_CACHE_TTL;
    }
    return contributorsCacheTtl;
  }

  private Duration effectiveRetryInterval() {
    if (contributorsRetryInterval == null
        || contributorsRetryInterval.isNegative()
        || contributorsRetryInterval.isZero()) {
      return Duration.ofMinutes(15);
    }
    return contributorsRetryInterval;
  }

  private boolean shouldRefresh(ContributorsCacheSnapshot snapshot, Instant now) {
    if (snapshot.loadedAt() != null && now.isBefore(snapshot.loadedAt().plus(effectiveCacheTtl()))) {
      return false;
    }
    if (snapshot.lastAttemptAt() == null) {
      return true;
    }
    return now.isAfter(snapshot.lastAttemptAt().plus(effectiveRetryInterval()));
  }

  private String getGithubClientId() {
    return config.getOptionalValue("GH_CLIENT_ID", String.class).orElse("");
  }

  private String getGithubClientSecret() {
    return config.getOptionalValue("GH_CLIENT_SECRET", String.class).orElse("");
  }

  private String getGithubApiToken() {
    return config.getOptionalValue("GH_TOKEN", String.class).orElse("").trim();
  }

  private String url(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  public record GithubProfile(String login, String htmlUrl, String avatarUrl, String id, String email) {
  }

  public record GithubContributor(String login, String avatarUrl, String htmlUrl, int contributions) {
  }

  private record ContributorsCacheSnapshot(
      List<GithubContributor> contributors,
      Instant loadedAt,
      Instant lastAttemptAt,
      long lastLoadDurationMs,
      boolean lastAttemptSucceeded) {
    static ContributorsCacheSnapshot empty() {
      return new ContributorsCacheSnapshot(List.of(), null, null, 0L, false);
    }
  }
}
