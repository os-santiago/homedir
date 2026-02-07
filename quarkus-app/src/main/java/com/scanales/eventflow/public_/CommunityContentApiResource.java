package com.scanales.eventflow.public_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.eventflow.community.CommunityContentItem;
import com.scanales.eventflow.community.CommunityContentMetrics;
import com.scanales.eventflow.community.CommunityContentService;
import com.scanales.eventflow.community.CommunityScoreCalculator;
import com.scanales.eventflow.community.CommunityVoteAggregate;
import com.scanales.eventflow.community.CommunityVoteService;
import com.scanales.eventflow.community.CommunityVoteType;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Path("/api/community/content")
@Produces(MediaType.APPLICATION_JSON)
public class CommunityContentApiResource {
  private static final Logger LOG = Logger.getLogger(CommunityContentApiResource.class);
  private static final int MAX_LIMIT = 100;

  @Inject CommunityContentService contentService;
  @Inject CommunityVoteService voteService;
  @Inject SecurityIdentity identity;

  @ConfigProperty(name = "community.content.featured.window-days", defaultValue = "7")
  int featuredWindowDays;

  @ConfigProperty(name = "community.content.ranking.decay-enabled", defaultValue = "true")
  boolean decayEnabled;

  @GET
  public Response list(
      @QueryParam("view") String viewParam,
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    String view = normalizeView(viewParam);
    int limit = normalizeLimit(limitParam);
    int offset = Math.max(0, offsetParam == null ? 0 : offsetParam);

    List<CommunityContentItem> all = contentService.allItems();
    String userId = currentUserId().orElse(null);
    Map<String, CommunityVoteAggregate> aggregates = aggregatesFor(all, userId);

    List<RankedItem> ordered =
        switch (view) {
          case "featured" -> rankFeatured(all, aggregates);
          case "new" -> rankNew(all, aggregates);
          default -> rankFeatured(all, aggregates);
        };

    List<RankedItem> page = paginate(ordered, limit, offset);
    List<ContentItemResponse> items =
        page.stream().map(item -> toResponse(item.item(), item.aggregate(), item.score())).toList();

    CommunityContentMetrics metrics = contentService.metrics();
    CacheMeta cacheMeta =
        new CacheMeta(
            metrics.cacheSize(),
            metrics.lastLoadTime(),
            metrics.loadDurationMs(),
            metrics.filesLoaded(),
            metrics.filesInvalid());

    return Response.ok(new ContentListResponse(view, limit, offset, ordered.size(), items, cacheMeta)).build();
  }

  @GET
  @Path("{id}")
  public Response detail(@PathParam("id") String id) {
    Optional<CommunityContentItem> item = contentService.getById(id);
    if (item.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "content_not_found")).build();
    }
    String userId = currentUserId().orElse(null);
    Map<String, CommunityVoteAggregate> aggregates = voteService.getAggregates(List.of(id), userId);
    CommunityVoteAggregate aggregate = aggregates.getOrDefault(id, CommunityVoteAggregate.empty());
    double score =
        CommunityScoreCalculator.score(aggregate, item.get().createdAt(), Instant.now(), decayEnabled);
    return Response.ok(new ContentDetailResponse(toResponse(item.get(), aggregate, score))).build();
  }

  @PUT
  @Path("{id}/vote")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response vote(@PathParam("id") String id, VoteRequest request) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    if (request == null || request.vote() == null || request.vote().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "vote_required")).build();
    }
    Optional<CommunityVoteType> parsedVote = CommunityVoteType.fromApi(request.vote());
    if (parsedVote.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_vote")).build();
    }
    Optional<CommunityContentItem> content = contentService.getById(id);
    if (content.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "content_not_found")).build();
    }
    try {
      voteService.upsertVote(userId.get(), id, parsedVote.get());
    } catch (CommunityVoteService.RateLimitExceededException e) {
      return Response.status(429).entity(Map.of("error", "daily_vote_limit_reached")).build();
    } catch (Exception e) {
      LOG.error("Failed persisting community vote", e);
      return Response.serverError().entity(Map.of("error", "vote_persist_failed")).build();
    }
    CommunityVoteAggregate aggregate =
        voteService
            .getAggregates(List.of(id), userId.get())
            .getOrDefault(id, CommunityVoteAggregate.empty());
    double score =
        CommunityScoreCalculator.score(aggregate, content.get().createdAt(), Instant.now(), decayEnabled);
    return Response.ok(new VoteResponse(toResponse(content.get(), aggregate, score))).build();
  }

  private String normalizeView(String raw) {
    if (raw == null || raw.isBlank()) {
      return "featured";
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    if ("new".equals(normalized) || "featured".equals(normalized)) {
      return normalized;
    }
    return "featured";
  }

  private int normalizeLimit(Integer rawLimit) {
    if (rawLimit == null || rawLimit <= 0) {
      return 10;
    }
    return Math.min(rawLimit, MAX_LIMIT);
  }

  private Map<String, CommunityVoteAggregate> aggregatesFor(
      List<CommunityContentItem> all, String userId) {
    List<String> ids = all.stream().map(CommunityContentItem::id).toList();
    return voteService.getAggregates(ids, userId);
  }

  private List<RankedItem> rankNew(
      List<CommunityContentItem> all, Map<String, CommunityVoteAggregate> aggregates) {
    List<RankedItem> ranked = new ArrayList<>(all.size());
    Instant now = Instant.now();
    for (CommunityContentItem item : all) {
      CommunityVoteAggregate aggregate =
          aggregates.getOrDefault(item.id(), CommunityVoteAggregate.empty());
      double score = CommunityScoreCalculator.score(aggregate, item.createdAt(), now, decayEnabled);
      ranked.add(new RankedItem(item, aggregate, score));
    }
    ranked.sort(Comparator.comparing((RankedItem r) -> r.item().createdAt()).reversed());
    return ranked;
  }

  private List<RankedItem> rankFeatured(
      List<CommunityContentItem> all, Map<String, CommunityVoteAggregate> aggregates) {
    Instant now = Instant.now();
    Instant cutoff = now.minus(Duration.ofDays(Math.max(1, featuredWindowDays)));
    List<RankedItem> ranked = new ArrayList<>();
    for (CommunityContentItem item : all) {
      if (item.createdAt().isBefore(cutoff)) {
        continue;
      }
      CommunityVoteAggregate aggregate =
          aggregates.getOrDefault(item.id(), CommunityVoteAggregate.empty());
      double score = CommunityScoreCalculator.score(aggregate, item.createdAt(), now, decayEnabled);
      ranked.add(new RankedItem(item, aggregate, score));
    }
    if (ranked.isEmpty()) {
      ranked = rankNew(all, aggregates);
    } else {
      ranked.sort(
          Comparator.comparingDouble((RankedItem r) -> r.score())
              .reversed()
              .thenComparing(r -> r.item().createdAt(), Comparator.reverseOrder()));
    }
    return ranked;
  }

  private List<RankedItem> paginate(List<RankedItem> ranked, int limit, int offset) {
    if (offset >= ranked.size()) {
      return List.of();
    }
    int end = Math.min(ranked.size(), offset + limit);
    return ranked.subList(offset, end);
  }

  private ContentItemResponse toResponse(
      CommunityContentItem item, CommunityVoteAggregate aggregate, double score) {
    VoteCounts counts = new VoteCounts(aggregate.recommended(), aggregate.mustSee(), aggregate.notForMe());
    String myVote = aggregate.myVote() == null ? null : aggregate.myVote().apiValue();
    return new ContentItemResponse(
        item.id(),
        item.title(),
        item.url(),
        item.summary(),
        item.source(),
        item.createdAt(),
        item.publishedAt(),
        item.tags(),
        item.author(),
        counts,
        myVote,
        score);
  }

  private Optional<String> currentUserId() {
    if (identity == null || identity.isAnonymous()) {
      return Optional.empty();
    }
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return Optional.of(email.toLowerCase(Locale.ROOT));
    }
    String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    if (principal != null && !principal.isBlank()) {
      return Optional.of(principal.toLowerCase(Locale.ROOT));
    }
    String sub = AdminUtils.getClaim(identity, "sub");
    if (sub != null && !sub.isBlank()) {
      return Optional.of(sub);
    }
    return Optional.empty();
  }

  private record RankedItem(CommunityContentItem item, CommunityVoteAggregate aggregate, double score) {
  }

  public record VoteRequest(String vote) {
  }

  public record VoteResponse(ContentItemResponse item) {
  }

  public record ContentDetailResponse(ContentItemResponse item) {
  }

  public record ContentListResponse(
      String view,
      int limit,
      int offset,
      int total,
      List<ContentItemResponse> items,
      CacheMeta cache) {
  }

  public record CacheMeta(
      @JsonProperty("cache_size") int cacheSize,
      @JsonProperty("last_load_time") Instant lastLoadTime,
      @JsonProperty("load_duration_ms") long loadDurationMs,
      @JsonProperty("files_loaded") int filesLoaded,
      @JsonProperty("files_invalid") int filesInvalid) {
  }

  public record ContentItemResponse(
      String id,
      String title,
      String url,
      String summary,
      String source,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("published_at") Instant publishedAt,
      List<String> tags,
      String author,
      @JsonProperty("vote_counts") VoteCounts voteCounts,
      @JsonProperty("my_vote") String myVote,
      double score) {
  }

  public record VoteCounts(
      long recommended, @JsonProperty("must_see") long mustSee, @JsonProperty("not_for_me") long notForMe) {
  }
}

