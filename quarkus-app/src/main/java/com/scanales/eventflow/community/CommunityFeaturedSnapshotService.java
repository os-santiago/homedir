package com.scanales.eventflow.community;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CommunityFeaturedSnapshotService {
  private static final Logger LOG = Logger.getLogger(CommunityFeaturedSnapshotService.class);

  @Inject CommunityContentService contentService;
  @Inject CommunityVoteService voteService;

  @ConfigProperty(name = "community.content.featured.window-days", defaultValue = "7")
  int featuredWindowDays;

  @ConfigProperty(name = "community.content.ranking.decay-enabled", defaultValue = "true")
  boolean decayEnabled;

  @ConfigProperty(name = "community.content.featured.snapshot-max-age", defaultValue = "PT45S")
  Duration snapshotMaxAge;

  @ConfigProperty(name = "community.content.featured.response-cache-ttl", defaultValue = "PT10S")
  Duration responseCacheTtl;

  private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());
  private final Map<ResponseKey, ResponseEntry> responseCache = new ConcurrentHashMap<>();
  private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
  private final ExecutorService refreshExecutor =
      Executors.newSingleThreadExecutor(
          new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
              Thread t = new Thread(r, "community-featured-refresh");
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

  @Scheduled(every = "{community.content.featured.snapshot-refresh-every:30s}")
  void scheduledRefresh() {
    triggerRefreshAsync(true, "schedule");
  }

  public FeaturedPage page(String filter, int limit, int offset) {
    maybeRefreshAsyncOnDemand();
    Snapshot current = snapshot.get();
    String normalizedFilter = normalizeFilter(filter);
    List<FeaturedItem> ranked = current.rankedByFilter().getOrDefault(normalizedFilter, List.of());
    int total = ranked.size();
    if (offset >= total) {
      return new FeaturedPage(List.of(), total);
    }
    ResponseKey key = new ResponseKey(normalizedFilter, limit, offset);
    ResponseEntry cached = responseCache.get(key);
    Instant now = Instant.now();
    if (cached != null && now.isBefore(cached.cachedAt().plus(responseCacheTtl))) {
      return new FeaturedPage(cached.items(), cached.total());
    }
    int end = Math.min(total, offset + limit);
    List<FeaturedItem> page = List.copyOf(ranked.subList(offset, end));
    responseCache.put(key, new ResponseEntry(page, total, now));
    return new FeaturedPage(page, total);
  }

  public void onVotesUpdated() {
    responseCache.clear();
    triggerRefreshAsync(true, "vote-updated");
  }

  public void refreshNowForTests() {
    refreshNow("test");
  }

  private void maybeRefreshAsyncOnDemand() {
    Snapshot current = snapshot.get();
    if (current.refreshedAt() == null) {
      triggerRefreshAsync(false, "empty-snapshot");
      return;
    }
    if (Instant.now().isAfter(current.refreshedAt().plus(snapshotMaxAge))) {
      triggerRefreshAsync(false, "stale-snapshot");
    }
  }

  private void triggerRefreshAsync(boolean force, String reason) {
    Snapshot current = snapshot.get();
    boolean stale =
        current.refreshedAt() == null
            || Instant.now().isAfter(current.refreshedAt().plus(snapshotMaxAge));
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
    List<CommunityContentItem> allItems = contentService.allItems();
    List<String> ids = allItems.stream().map(CommunityContentItem::id).toList();
    Map<String, CommunityVoteAggregate> aggregates = voteService.getAggregates(ids, null);
    Instant now = Instant.now();

    List<FeaturedItem> allRanked = rankForFilter(allItems, aggregates, now);
    List<FeaturedItem> internetRanked =
        rankForFilter(
            allItems.stream().filter(item -> detectOrigin(item) == ContentOrigin.INTERNET).toList(),
            aggregates,
            now);
    List<FeaturedItem> membersRanked =
        rankForFilter(
            allItems.stream().filter(item -> detectOrigin(item) == ContentOrigin.MEMBERS).toList(),
            aggregates,
            now);

    Map<String, List<FeaturedItem>> byFilter = new HashMap<>();
    byFilter.put("all", List.copyOf(allRanked));
    byFilter.put("internet", List.copyOf(internetRanked));
    byFilter.put("members", List.copyOf(membersRanked));

    Snapshot refreshed =
        new Snapshot(
            Map.copyOf(byFilter),
            Instant.now(),
            Duration.between(started, Instant.now()).toMillis());
    snapshot.set(refreshed);
    responseCache.clear();
    LOG.infov(
        "Community featured snapshot refreshed reason={0} all={1} internet={2} members={3} durationMs={4}",
        reason,
        allRanked.size(),
        internetRanked.size(),
        membersRanked.size(),
        refreshed.durationMs());
  }

  private List<FeaturedItem> rankForFilter(
      List<CommunityContentItem> filteredItems,
      Map<String, CommunityVoteAggregate> aggregates,
      Instant now) {
    List<CommunityContentItem> candidates = featuredCandidates(filteredItems, now);
    List<FeaturedItem> ranked = new ArrayList<>();
    for (CommunityContentItem item : candidates) {
      CommunityVoteAggregate aggregate =
          aggregates.getOrDefault(item.id(), CommunityVoteAggregate.empty());
      double score = CommunityScoreCalculator.score(aggregate, item.createdAt(), now, decayEnabled);
      ranked.add(new FeaturedItem(item, aggregate, score));
    }
    ranked.sort(
        Comparator.comparingDouble((FeaturedItem r) -> r.score())
            .reversed()
            .thenComparing(r -> r.item().createdAt(), Comparator.reverseOrder()));
    return ranked;
  }

  private List<CommunityContentItem> featuredCandidates(List<CommunityContentItem> filtered, Instant now) {
    Instant cutoff = now.minus(Duration.ofDays(Math.max(1, featuredWindowDays)));
    List<CommunityContentItem> candidates = new ArrayList<>();
    for (CommunityContentItem item : filtered) {
      if (!item.createdAt().isBefore(cutoff)) {
        candidates.add(item);
      }
    }
    return candidates.isEmpty() ? filtered : candidates;
  }

  private String normalizeFilter(String raw) {
    if (raw == null || raw.isBlank()) {
      return "all";
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "internet" -> "internet";
      case "members" -> "members";
      default -> "all";
    };
  }

  private ContentOrigin detectOrigin(CommunityContentItem item) {
    if (item == null) {
      return ContentOrigin.INTERNET;
    }
    String id = item.id() == null ? "" : item.id().toLowerCase(Locale.ROOT);
    String source = item.source() == null ? "" : item.source().trim().toLowerCase(Locale.ROOT);
    if (id.startsWith("submission-") || "community member".equals(source) || "member".equals(source)) {
      return ContentOrigin.MEMBERS;
    }
    return ContentOrigin.INTERNET;
  }

  public record FeaturedPage(List<FeaturedItem> items, int total) {
  }

  public record FeaturedItem(CommunityContentItem item, CommunityVoteAggregate aggregate, double score) {
  }

  private record Snapshot(Map<String, List<FeaturedItem>> rankedByFilter, Instant refreshedAt, long durationMs) {
    static Snapshot empty() {
      return new Snapshot(Map.of(), null, 0L);
    }
  }

  private record ResponseKey(String filter, int limit, int offset) {
  }

  private record ResponseEntry(List<FeaturedItem> items, int total, Instant cachedAt) {
  }

  private enum ContentOrigin {
    INTERNET,
    MEMBERS
  }
}
