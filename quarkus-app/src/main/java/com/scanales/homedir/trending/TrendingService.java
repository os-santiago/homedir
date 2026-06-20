package com.scanales.homedir.trending;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.homedir.service.PersistenceService;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TrendingService {

  private static final Logger LOG = Logger.getLogger(TrendingService.class);
  private static final String GITHUB_TRENDING_URL = "https://github.com/trending";

  @ConfigProperty(name = "trending.request-timeout", defaultValue = "PT15S")
  Duration requestTimeout;

  @ConfigProperty(name = "trending.cache-ttl", defaultValue = "PT48H")
  Duration cacheTtl;

  @ConfigProperty(name = "trending.default-count", defaultValue = "10")
  int defaultCount;

  @Inject
  PersistenceService persistenceService;

  @Inject
  ObjectMapper objectMapper;

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(15))
      .build();

  private final AtomicReference<TrendingCacheSnapshot> dailyCache = new AtomicReference<>(TrendingCacheSnapshot.empty(TrendingPeriod.DAILY));
  private final AtomicReference<TrendingCacheSnapshot> weeklyCache = new AtomicReference<>(TrendingCacheSnapshot.empty(TrendingPeriod.WEEKLY));
  private final AtomicReference<TrendingCacheSnapshot> monthlyCache = new AtomicReference<>(TrendingCacheSnapshot.empty(TrendingPeriod.MONTHLY));

  private final AtomicBoolean dailyRefreshInProgress = new AtomicBoolean(false);
  private final AtomicBoolean weeklyRefreshInProgress = new AtomicBoolean(false);
  private final AtomicBoolean monthlyRefreshInProgress = new AtomicBoolean(false);

  private static final Pattern REPO_PATTERN = Pattern.compile(
      "<h2[^>]*>.*?<a\\s+href=\"/([^/]+)/([^\"]+)\"[^>]*>.*?</a>.*?</h2>",
      Pattern.DOTALL
  );

  private static final Pattern DESCRIPTION_PATTERN = Pattern.compile(
      "<p[^>]*class=\"[^\"]*col-9[^\"]*\"[^>]*>\\s*([^<]+)\\s*</p>",
      Pattern.DOTALL
  );

  private static final Pattern STARS_PATTERN = Pattern.compile(
      "([\\d,]+)\\s*stars\\s+(?:today|this\\s+week|this\\s+month)",
      Pattern.CASE_INSENSITIVE
  );

  private static final Pattern LANGUAGE_PATTERN = Pattern.compile(
      "<span[^>]*itemprop=\"programmingLanguage\"[^>]*>\\s*([^<]+)\\s*</span>",
      Pattern.DOTALL
  );

  @PostConstruct
  void init() {
    loadCachesFromDisk();
    refreshAllAsync("startup");
  }

  @Scheduled(cron = "${trending.daily.cron:0 0 * * ?}")
  void refreshDaily() {
    refreshAsync(TrendingPeriod.DAILY, "scheduled");
  }

  @Scheduled(cron = "${trending.weekly.cron:0 0 * * MON}")
  void refreshWeekly() {
    refreshAsync(TrendingPeriod.WEEKLY, "scheduled");
  }

  @Scheduled(cron = "${trending.monthly.cron:0 0 1 * *}")
  void refreshMonthly() {
    refreshAsync(TrendingPeriod.MONTHLY, "scheduled");
  }

  public List<TrendingRepo> getTrending(TrendingPeriod period, int count) {
    TrendingCacheSnapshot snapshot = getCache(period);

    if (snapshot.isStale(cacheTtl)) {
      refreshAsync(period, "on-demand");
    }

    List<TrendingRepo> repos = snapshot.repos();
    int limit = Math.max(1, Math.min(count, 10));

    if (repos.size() <= limit) {
      return repos;
    }

    return repos.subList(0, limit);
  }

  private void refreshAllAsync(String reason) {
    refreshAsync(TrendingPeriod.DAILY, reason);
    refreshAsync(TrendingPeriod.WEEKLY, reason);
    refreshAsync(TrendingPeriod.MONTHLY, reason);
  }

  private void refreshAsync(TrendingPeriod period, String reason) {
    AtomicBoolean inProgress = getRefreshFlag(period);

    if (!inProgress.compareAndSet(false, true)) {
      return;
    }

    Thread.ofVirtual().start(() -> {
      try {
        refresh(period, reason);
      } finally {
        inProgress.set(false);
      }
    });
  }

  private void refresh(TrendingPeriod period, String reason) {
    Instant startedAt = Instant.now();

    try {
      List<TrendingRepo> repos = scrapeGithubTrending(period);

      if (!repos.isEmpty()) {
        TrendingCacheSnapshot snapshot = new TrendingCacheSnapshot(
            List.copyOf(repos),
            Instant.now(),
            Instant.now(),
            period
        );

        setCache(period, snapshot);
        saveCacheToDisk(period, snapshot);

        LOG.infof("Refreshed trending %s cache: %d repos (reason=%s)", period.toGithubPath(), repos.size(), reason);
      } else {
        TrendingCacheSnapshot current = getCache(period);
        TrendingCacheSnapshot failed = new TrendingCacheSnapshot(
            current.repos(),
            current.lastRefreshTime(),
            Instant.now(),
            period
        );
        setCache(period, failed);

        LOG.warnf("Failed to refresh trending %s cache, keeping existing %d repos", period.toGithubPath(), current.repos().size());
      }
    } catch (Exception e) {
      LOG.errorf(e, "Error refreshing trending %s cache", period.toGithubPath());

      TrendingCacheSnapshot current = getCache(period);
      TrendingCacheSnapshot failed = new TrendingCacheSnapshot(
          current.repos(),
          current.lastRefreshTime(),
          Instant.now(),
          period
      );
      setCache(period, failed);
    }
  }

  private List<TrendingRepo> scrapeGithubTrending(TrendingPeriod period) {
    try {
      String url = GITHUB_TRENDING_URL + "?since=" + period.toGithubPath();

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(requestTimeout)
          .header("User-Agent", "Mozilla/5.0 (compatible; HomedirBot/1.0)")
          .header("Accept", "text/html")
          .GET()
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 429) {
        LOG.warn("GitHub rate limited, waiting before retry");
        Thread.sleep(60000);
        return List.of();
      }

      if (response.statusCode() >= 400) {
        LOG.warnf("GitHub trending returned status %d", response.statusCode());
        return List.of();
      }

      return parseHtml(response.body());

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Scraping interrupted", e);
      return List.of();
    } catch (IOException e) {
      LOG.warn("Failed to scrape GitHub trending", e);
      return List.of();
    }
  }

  private List<TrendingRepo> parseHtml(String html) {
    List<TrendingRepo> repos = new ArrayList<>();

    String[] articles = html.split("<article\\s+class=\"[^\"]*Box-row[^\"]*\"");

    for (int i = 1; i < articles.length && repos.size() < defaultCount; i++) {
      String article = articles[i];

      Matcher repoMatcher = REPO_PATTERN.matcher(article);
      if (!repoMatcher.find()) {
        continue;
      }

      String owner = repoMatcher.group(1);
      String name = repoMatcher.group(2);

      String description = "";
      Matcher descMatcher = DESCRIPTION_PATTERN.matcher(article);
      if (descMatcher.find()) {
        description = descMatcher.group(1).trim();
      }

      int stars = 0;
      Matcher starsMatcher = STARS_PATTERN.matcher(article);
      if (starsMatcher.find()) {
        String starsStr = starsMatcher.group(1).replace(",", "");
        try {
          stars = Integer.parseInt(starsStr);
        } catch (NumberFormatException ignored) {
        }
      }

      String language = "";
      Matcher langMatcher = LANGUAGE_PATTERN.matcher(article);
      if (langMatcher.find()) {
        language = langMatcher.group(1).trim();
      }

      String url = "https://github.com/" + owner + "/" + name;

      repos.add(new TrendingRepo(name, owner, description, stars, language, url));
    }

    return repos;
  }

  private TrendingCacheSnapshot getCache(TrendingPeriod period) {
    return switch (period) {
      case DAILY -> dailyCache.get();
      case WEEKLY -> weeklyCache.get();
      case MONTHLY -> monthlyCache.get();
    };
  }

  private void setCache(TrendingPeriod period, TrendingCacheSnapshot snapshot) {
    switch (period) {
      case DAILY -> dailyCache.set(snapshot);
      case WEEKLY -> weeklyCache.set(snapshot);
      case MONTHLY -> monthlyCache.set(snapshot);
    }
  }

  private AtomicBoolean getRefreshFlag(TrendingPeriod period) {
    return switch (period) {
      case DAILY -> dailyRefreshInProgress;
      case WEEKLY -> weeklyRefreshInProgress;
      case MONTHLY -> monthlyRefreshInProgress;
    };
  }

  private void loadCachesFromDisk() {
    loadCacheFromDisk(TrendingPeriod.DAILY);
    loadCacheFromDisk(TrendingPeriod.WEEKLY);
    loadCacheFromDisk(TrendingPeriod.MONTHLY);
  }

  private void loadCacheFromDisk(TrendingPeriod period) {
    try {
      java.nio.file.Path cacheFile = getCacheFilePath(period);
      if (!java.nio.file.Files.exists(cacheFile)) {
        return;
      }

      TrendingCacheSnapshot snapshot = objectMapper.readValue(
          cacheFile.toFile(),
          new TypeReference<TrendingCacheSnapshot>() {}
      );

      if (snapshot != null && snapshot.repos() != null) {
        setCache(period, snapshot);
        LOG.infof("Loaded trending %s cache from disk: %d repos", period.toGithubPath(), snapshot.repos().size());
      }
    } catch (IOException e) {
      LOG.warnf("Failed to load trending %s cache from disk: %s", period.toGithubPath(), e.getMessage());
    }
  }

  private void saveCacheToDisk(TrendingPeriod period, TrendingCacheSnapshot snapshot) {
    try {
      java.nio.file.Path cacheFile = getCacheFilePath(period);
      java.nio.file.Files.createDirectories(cacheFile.getParent());

      objectMapper.writerWithDefaultPrettyPrinter().writeValue(cacheFile.toFile(), snapshot);
    } catch (IOException e) {
      LOG.warnf("Failed to save trending %s cache to disk: %s", period.toGithubPath(), e.getMessage());
    }
  }

  private java.nio.file.Path getCacheFilePath(TrendingPeriod period) {
    String dataDir = System.getProperty("homedir.data.dir", "data");
    return java.nio.file.Paths.get(dataDir, "trending", "trending-" + period.toGithubPath() + ".json");
  }
}
