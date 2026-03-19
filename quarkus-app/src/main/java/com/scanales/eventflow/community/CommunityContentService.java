package com.scanales.eventflow.community;

import com.scanales.eventflow.util.PaginationGuardrails;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
  private static final int MAX_LIMIT = PaginationGuardrails.MAX_PAGE_LIMIT;
  private static final int MAX_OFFSET = PaginationGuardrails.MAX_OFFSET;
  static final String BUNDLED_SEED_ROOT = "community-seed/";
  static final String BUNDLED_SEED_INDEX = BUNDLED_SEED_ROOT + "index.txt";
  static final String MANAGED_SEED_MANIFEST = ".bundled-seed-manifest";

  @ConfigProperty(name = "homedir.data.dir", defaultValue = "data")
  String dataDirPath;

  @ConfigProperty(name = "community.content.dir")
  Optional<String> configuredDir;

  @ConfigProperty(name = "community.content.cache-ttl", defaultValue = "PT1H")
  Duration cacheTtl;

  @ConfigProperty(name = "community.content.seed.enabled", defaultValue = "true")
  boolean starterContentEnabled;

  private final CommunityContentParser parser = new CommunityContentParser();
  private final List<CommunityContentItem> starterItems = buildStarterItems();
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
    return paginate(effectiveItems(cache.get()), limit, offset);
  }

  public List<CommunityContentItem> allItems() {
    maybeRefreshAsyncOnDemand();
    return effectiveItems(cache.get());
  }

  public Optional<CommunityContentItem> getById(String id) {
    if (id == null || id.isBlank()) {
      return Optional.empty();
    }
    maybeRefreshAsyncOnDemand();
    CacheSnapshot snapshot = cache.get();
    CommunityContentItem item = snapshot.byId().get(id);
    if (item != null) {
      return Optional.of(item);
    }
    if (!useStarterContent(snapshot)) {
      return Optional.empty();
    }
    return starterItems.stream().filter(candidate -> id.equals(candidate.id())).findFirst();
  }

  public boolean containsUrl(String normalizedUrl) {
    if (normalizedUrl == null || normalizedUrl.isBlank()) {
      return false;
    }
    maybeRefreshAsyncOnDemand();
    CacheSnapshot snapshot = cache.get();
    if (snapshot.urls().contains(normalizedUrl)) {
      return true;
    }
    if (!useStarterContent(snapshot)) {
      return false;
    }
    return starterItems.stream().anyMatch(item -> normalizedUrl.equals(item.url()));
  }

  public CommunityContentMetrics metrics() {
    CacheSnapshot snapshot = cache.get();
    int size = effectiveItems(snapshot).size();
    return new CommunityContentMetrics(
        size,
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
      if (starterContentEnabled) {
        SeedSyncResult seedSync = syncBundledSeedContent(dir, bundledSeedClassLoader());
        LOG.infov(
            "Bundled community seed synced total={0} written={1} removed={2}",
            seedSync.totalFiles(),
            seedSync.writtenFiles(),
            seedSync.removedFiles());
      }
      if (!Files.exists(dir)) {
        LOG.warn("Community content directory is not available after seed sync");
      } else if (!Files.isDirectory(dir)) {
        LOG.warn("Community content path is not a directory");
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
    int limit =
        PaginationGuardrails.clampLimit(
            requestedLimit, PaginationGuardrails.DEFAULT_PAGE_LIMIT, MAX_LIMIT);
    int offset = PaginationGuardrails.clampOffset(requestedOffset, MAX_OFFSET);
    if (offset >= source.size()) {
      return List.of();
    }
    int end = Math.min(source.size(), offset + limit);
    return source.subList(offset, end);
  }

  private List<CommunityContentItem> effectiveItems(CacheSnapshot snapshot) {
    if (snapshot == null) {
      return starterContentEnabled ? starterItems : List.of();
    }
    if (useStarterContent(snapshot)) {
      return starterItems;
    }
    return snapshot.items();
  }

  private boolean useStarterContent(CacheSnapshot snapshot) {
    return starterContentEnabled && snapshot != null && snapshot.items().isEmpty();
  }

  private static ClassLoader bundledSeedClassLoader() {
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    return contextLoader != null ? contextLoader : CommunityContentService.class.getClassLoader();
  }

  static SeedSyncResult syncBundledSeedContent(Path contentDir, ClassLoader classLoader)
      throws IOException {
    Files.createDirectories(contentDir);
    List<String> indexedFiles = readBundledSeedIndex(classLoader);
    Set<String> currentFiles = new HashSet<>(indexedFiles);
    Set<String> previousFiles = readManagedSeedManifest(contentDir);
    int writtenFiles = 0;
    int removedFiles = 0;

    for (String fileName : indexedFiles) {
      byte[] bundledBytes = readBundledSeedResource(classLoader, fileName);
      Path target = managedSeedTarget(contentDir, fileName);
      if (!Files.exists(target) || !Arrays.equals(Files.readAllBytes(target), bundledBytes)) {
        Files.write(
            target,
            bundledBytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
        writtenFiles++;
      }
    }

    for (String previousFile : previousFiles) {
      if (currentFiles.contains(previousFile)) {
        continue;
      }
      Path target = managedSeedTarget(contentDir, previousFile);
      if (Files.exists(target) && Files.isRegularFile(target)) {
        Files.delete(target);
        removedFiles++;
      }
    }

    writeManagedSeedManifest(contentDir, indexedFiles);
    return new SeedSyncResult(indexedFiles.size(), writtenFiles, removedFiles);
  }

  static List<String> readBundledSeedIndex(ClassLoader classLoader) throws IOException {
    try (InputStream stream = classLoader.getResourceAsStream(BUNDLED_SEED_INDEX)) {
      if (stream == null) {
        return List.of();
      }
      return new String(stream.readAllBytes())
          .lines()
          .map(String::trim)
          .filter(line -> !line.isBlank())
          .distinct()
          .toList();
    }
  }

  private static byte[] readBundledSeedResource(ClassLoader classLoader, String fileName)
      throws IOException {
    try (InputStream stream = classLoader.getResourceAsStream(BUNDLED_SEED_ROOT + fileName)) {
      if (stream == null) {
        throw new IOException("Missing bundled community seed resource");
      }
      return stream.readAllBytes();
    }
  }

  private static Set<String> readManagedSeedManifest(Path contentDir) throws IOException {
    Path manifest = contentDir.resolve(MANAGED_SEED_MANIFEST);
    if (!Files.exists(manifest)) {
      return Set.of();
    }
    return Files.readAllLines(manifest).stream()
        .map(String::trim)
        .filter(line -> !line.isBlank())
        .collect(java.util.stream.Collectors.toCollection(HashSet::new));
  }

  private static void writeManagedSeedManifest(Path contentDir, List<String> indexedFiles)
      throws IOException {
    Path manifest = contentDir.resolve(MANAGED_SEED_MANIFEST);
    String body = String.join(System.lineSeparator(), indexedFiles);
    String normalizedBody = body.isBlank() ? "" : body + System.lineSeparator();
    Files.writeString(
        manifest,
        normalizedBody,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE);
  }

  private static Path managedSeedTarget(Path contentDir, String fileName) {
    String normalized = fileName == null ? "" : fileName.trim();
    if (normalized.isBlank()
        || normalized.contains("/")
        || normalized.contains("\\")
        || !(normalized.endsWith(".yml") || normalized.endsWith(".yaml"))) {
      throw new IllegalArgumentException("Invalid bundled community seed file");
    }
    return contentDir.resolve(normalized).normalize();
  }

  private static List<CommunityContentItem> buildStarterItems() {
    Instant now = Instant.now();
    return List.of(
        new CommunityContentItem(
            "starter-ai-platform-01",
            "AI platform ops: practical guardrails for production teams",
            "https://github.blog/ai-and-ml/",
            "A practical signal for teams scaling AI features with delivery, safety, and observability guardrails.",
            "github.blog",
            "https://github.blog/wp-content/uploads/2023/03/GitHub-Blog-Feature-Image-2.png",
            now.minus(Duration.ofHours(9)),
            null,
            List.of("AI", "Platform Engineering", "MLOps"),
            "HomeDir Curator",
            CommunityContentMedia.ARTICLE_BLOG),
        new CommunityContentItem(
            "starter-cloudnative-02",
            "Cloud native in 2026: platform engineering patterns that actually stick",
            "https://www.cncf.io/blog/",
            "Curated perspective on platform teams, cloud native reliability, and fast feedback loops for developers.",
            "cncf.io",
            "https://www.cncf.io/wp-content/uploads/2018/03/cncf-color.png",
            now.minus(Duration.ofHours(18)),
            null,
            List.of("Cloud Native", "Platform Engineering", "DevOps"),
            "HomeDir Curator",
            CommunityContentMedia.ARTICLE_BLOG),
        new CommunityContentItem(
            "starter-video-03",
            "Kubernetes and platform engineering explained in one short video",
            "https://www.youtube.com/watch?v=X48VuDVv0do",
            "Quick visual recap for engineering teams navigating Kubernetes, platform APIs, and developer experience.",
            "youtube.com",
            null,
            now.minus(Duration.ofHours(28)),
            null,
            List.of("Kubernetes", "Platform Engineering", "Cloud Native"),
            "HomeDir Curator",
            CommunityContentMedia.VIDEO_STORY));
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

  record SeedSyncResult(int totalFiles, int writtenFiles, int removedFiles) {}
}
