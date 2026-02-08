package com.scanales.eventflow.community;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CommunityContentService {
  private static final Logger LOG = Logger.getLogger(CommunityContentService.class);
  private static final int MAX_LIMIT = 100;

  @ConfigProperty(name = "homedir.data.dir", defaultValue = "data")
  String dataDirPath;

  @ConfigProperty(name = "community.content.dir")
  Optional<String> configuredDir;

  @ConfigProperty(name = "community.content.cache-ttl", defaultValue = "PT1H")
  Duration cacheTtl;

  private final CommunityContentParser parser = new CommunityContentParser();
  private final AtomicReference<CacheSnapshot> cache = new AtomicReference<>(CacheSnapshot.empty());
  private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
  private final ExecutorService refreshExecutor =
      Executors.newSingleThreadExecutor(
          new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
              Thread t = new Thread(r, "community-content-refresh");
              t.setDaemon(true);
              return t;
            }
          });

  @PostConstruct
  void init() {
    refreshNow("startup");
  }

  @PreDestroy
  void shutdown() {
    refreshExecutor.shutdownNow();
  }

  @Scheduled(every = "{community.content.refresh-interval:15m}")
  void scheduledRefresh() {
    triggerRefreshAsync(true, "schedule");
  }

  public List<CommunityContentItem> listNew(int limit, int offset) {
    maybeRefreshAsyncOnDemand();
    return paginate(cache.get().items(), limit, offset);
  }

  public List<CommunityContentItem> allItems() {
    maybeRefreshAsyncOnDemand();
    return cache.get().items();
  }

  public Optional<CommunityContentItem> getById(String id) {
    if (id == null || id.isBlank()) {
      return Optional.empty();
    }
    maybeRefreshAsyncOnDemand();
    return Optional.ofNullable(cache.get().byId().get(id));
  }

  public boolean containsUrl(String normalizedUrl) {
    if (normalizedUrl == null || normalizedUrl.isBlank()) {
      return false;
    }
    maybeRefreshAsyncOnDemand();
    return cache.get().urls().contains(normalizedUrl);
  }

  public CommunityContentMetrics metrics() {
    CacheSnapshot snapshot = cache.get();
    return new CommunityContentMetrics(
        snapshot.items().size(),
        snapshot.loadedAt(),
        snapshot.loadDurationMs(),
        snapshot.filesLoaded(),
        snapshot.filesInvalid());
  }

  public void refreshNowForTests() {
    refreshNow("test");
  }

  public void forceRefreshAsync(String reason) {
    triggerRefreshAsync(true, reason == null || reason.isBlank() ? "manual" : reason);
  }

  private void maybeRefreshAsyncOnDemand() {
    CacheSnapshot snapshot = cache.get();
    if (snapshot.loadedAt() == null) {
      triggerRefreshAsync(false, "empty-cache");
      return;
    }
    if (Instant.now().isAfter(snapshot.loadedAt().plus(cacheTtl))) {
      triggerRefreshAsync(false, "ttl-expired");
    }
  }

  private void triggerRefreshAsync(boolean force, String reason) {
    CacheSnapshot snapshot = cache.get();
    boolean stale =
        snapshot.loadedAt() == null || Instant.now().isAfter(snapshot.loadedAt().plus(cacheTtl));
    if (!force && !stale) {
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

  private void refreshNow(String reason) {
    Instant started = Instant.now();
    Path dir = resolveContentDir();
    int filesLoaded = 0;
    int filesInvalid = 0;
    Map<String, CommunityContentItem> byId = new HashMap<>();
    Set<String> urls = new HashSet<>();
    List<CommunityContentItem> items = new ArrayList<>();
    try {
      if (!Files.exists(dir)) {
        LOG.warnf("Community content directory does not exist: %s", dir.toAbsolutePath());
      } else if (!Files.isDirectory(dir)) {
        LOG.warnf("Community content path is not a directory: %s", dir.toAbsolutePath());
      } else {
        try (var stream = Files.list(dir)) {
          List<Path> files =
              stream
                  .filter(Files::isRegularFile)
                  .filter(this::isSupportedFile)
                  .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                  .toList();
          for (Path file : files) {
            var outcome = parser.parse(file);
            if (!outcome.isValid()) {
              filesInvalid++;
              LOG.warnf(
                  "Skipping invalid community content file %s: %s",
                  file.getFileName(), outcome.error());
              continue;
            }
            CommunityContentItem item = outcome.item();
            CommunityContentItem existing = byId.get(item.id());
            if (existing != null) {
              filesInvalid++;
              LOG.warnf(
                  "Duplicate community content id=%s in file %s (keeping most recent)",
                  item.id(), file.getFileName());
              if (item.createdAt().isAfter(existing.createdAt())) {
                byId.put(item.id(), item);
              }
              continue;
            }
            byId.put(item.id(), item);
            if (item.url() != null && !item.url().isBlank()) {
              urls.add(item.url());
            }
            filesLoaded++;
          }
        }
      }
      items.addAll(byId.values());
      items.sort(Comparator.comparing(CommunityContentItem::createdAt).reversed());
    } catch (Exception e) {
      LOG.error("Failed loading curated community content", e);
    }
    long durationMs = Duration.between(started, Instant.now()).toMillis();
    CacheSnapshot snapshot =
        new CacheSnapshot(
            List.copyOf(items),
            Map.copyOf(byId),
            Set.copyOf(urls),
            Instant.now(),
            durationMs,
            filesLoaded,
            filesInvalid);
    cache.set(snapshot);
    LOG.infov(
        "Community content cache refreshed reason={0} items={1} loaded={2} invalid={3} durationMs={4}",
        reason,
        items.size(),
        filesLoaded,
        filesInvalid,
        durationMs);
  }

  private boolean isSupportedFile(Path path) {
    String name = path.getFileName().toString().toLowerCase();
    return name.endsWith(".yml") || name.endsWith(".yaml");
  }

  private Path resolveContentDir() {
    String configured = configuredDir.orElse("").trim();
    if (!configured.isEmpty()) {
      return Paths.get(configured);
    }
    String sysProp = System.getProperty("homedir.data.dir");
    String base = (sysProp != null && !sysProp.isBlank()) ? sysProp : dataDirPath;
    return Paths.get(base, "community", "content");
  }

  private static List<CommunityContentItem> paginate(
      List<CommunityContentItem> source, int requestedLimit, int requestedOffset) {
    int limit = requestedLimit <= 0 ? 20 : Math.min(requestedLimit, MAX_LIMIT);
    int offset = Math.max(0, requestedOffset);
    if (offset >= source.size()) {
      return List.of();
    }
    int end = Math.min(source.size(), offset + limit);
    return source.subList(offset, end);
  }

  private record CacheSnapshot(
      List<CommunityContentItem> items,
      Map<String, CommunityContentItem> byId,
      Set<String> urls,
      Instant loadedAt,
      long loadDurationMs,
      int filesLoaded,
      int filesInvalid) {
    static CacheSnapshot empty() {
      return new CacheSnapshot(List.of(), Map.of(), Set.of(), null, 0L, 0, 0);
    }
  }
}
