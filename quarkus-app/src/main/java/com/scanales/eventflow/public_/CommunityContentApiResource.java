package com.scanales.eventflow.public_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.eventflow.community.CommunityContentItem;
import com.scanales.eventflow.community.CommunityContentMedia;
import com.scanales.eventflow.community.CommunityContentMetrics;
import com.scanales.eventflow.community.CommunityContentService;
import com.scanales.eventflow.community.CommunityFeaturedSnapshotService;
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
import java.time.Instant;
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
  private static final int PAGE_SIZE = 10;

  @Inject CommunityContentService contentService;
  @Inject CommunityVoteService voteService;
  @Inject CommunityFeaturedSnapshotService featuredSnapshotService;
  @Inject SecurityIdentity identity;

  @ConfigProperty(name = "community.content.ranking.decay-enabled", defaultValue = "true")
  boolean decayEnabled;

  @GET
  public Response list(
      @QueryParam("view") String viewParam,
      @QueryParam("filter") String filterParam,
      @QueryParam("media") String mediaParam,
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    String view = normalizeView(viewParam);
    ContentFilter filter = normalizeFilter(filterParam);
    String mediaFilter = normalizeMediaFilter(mediaParam);
    int limit = normalizeLimit(limitParam);
    int offset = Math.max(0, offsetParam == null ? 0 : offsetParam);

    String userId = currentUserId().orElse(null);
    List<ContentItemResponse> items;
    int total;
    if ("new".equals(view)) {
      List<CommunityContentItem> all =
          applyMediaFilter(applyFilter(contentService.allItems(), filter), mediaFilter);
      List<CommunityContentItem> ordered = all;
      total = ordered.size();
      List<CommunityContentItem> pageItems = paginateItems(ordered, limit, offset);
      List<String> pageIds = pageItems.stream().map(CommunityContentItem::id).toList();
      Map<String, CommunityVoteAggregate> pageAggregates = voteService.getAggregates(pageIds, userId);
      Instant now = Instant.now();
      items =
          pageItems.stream()
              .map(
                  item -> {
                    CommunityVoteAggregate aggregate =
                        pageAggregates.getOrDefault(item.id(), CommunityVoteAggregate.empty());
                    double score =
                        CommunityScoreCalculator.score(aggregate, item.createdAt(), now, decayEnabled);
                    return toResponse(item, aggregate, score);
                  })
              .toList();
    } else {
      CommunityFeaturedSnapshotService.FeaturedPage featuredPage =
          featuredSnapshotService.page(filter.apiValue, mediaFilter, limit, offset);
      List<CommunityFeaturedSnapshotService.FeaturedItem> page = featuredPage.items();
      total = featuredPage.total();
      if (userId == null || userId.isBlank() || page.isEmpty()) {
        items =
            page.stream()
                .map(item -> toResponse(item.item(), item.aggregate(), item.score()))
                .toList();
      } else {
        List<String> pageIds =
            page.stream().map(item -> item.item().id()).toList();
        Map<String, CommunityVoteAggregate> userAggregates =
            voteService.getAggregates(pageIds, userId);
        items =
            page.stream()
                .map(
                    item -> {
                      CommunityVoteType myVote =
                          userAggregates.getOrDefault(item.item().id(), CommunityVoteAggregate.empty()).myVote();
                      CommunityVoteAggregate base = item.aggregate();
                      CommunityVoteAggregate aggregate =
                          new CommunityVoteAggregate(
                              base.recommended(),
                              base.mustSee(),
                              base.notForMe(),
                              myVote);
                      return toResponse(item.item(), aggregate, item.score());
                    })
                .toList();
      }
    }

    CommunityContentMetrics metrics = contentService.metrics();
    CacheMeta cacheMeta =
        new CacheMeta(
            metrics.cacheSize(),
            metrics.lastLoadTime(),
            metrics.loadDurationMs(),
            metrics.filesLoaded(),
            metrics.filesInvalid());

    return Response.ok(
            new ContentListResponse(
                view, filter.apiValue, mediaFilter, limit, offset, total, items, cacheMeta))
        .build();
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
      featuredSnapshotService.onVotesUpdated();
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

  private int normalizeLimit(Integer ignoredRawLimit) {
    return PAGE_SIZE;
  }

  private ContentFilter normalizeFilter(String rawFilter) {
    if (rawFilter == null || rawFilter.isBlank()) {
      return ContentFilter.ALL;
    }
    return switch (rawFilter.trim().toLowerCase(Locale.ROOT)) {
      case "internet" -> ContentFilter.INTERNET;
      case "members" -> ContentFilter.MEMBERS;
      default -> ContentFilter.ALL;
    };
  }

  private List<CommunityContentItem> applyFilter(List<CommunityContentItem> items, ContentFilter filter) {
    if (filter == ContentFilter.ALL) {
      return items;
    }
    return items.stream()
        .filter(item -> {
          ContentOrigin origin = detectOrigin(item);
          return filter == ContentFilter.MEMBERS
              ? origin == ContentOrigin.MEMBERS
              : origin == ContentOrigin.INTERNET;
        })
        .toList();
  }

  private List<CommunityContentItem> applyMediaFilter(
      List<CommunityContentItem> items, String mediaFilter) {
    return items.stream()
        .filter(item -> CommunityContentMedia.matchesFilter(item.mediaType(), mediaFilter))
        .toList();
  }

  private List<CommunityContentItem> paginateItems(List<CommunityContentItem> items, int limit, int offset) {
    if (offset >= items.size()) {
      return List.of();
    }
    int end = Math.min(items.size(), offset + limit);
    return items.subList(offset, end);
  }

  private ContentItemResponse toResponse(
      CommunityContentItem item, CommunityVoteAggregate aggregate, double score) {
    VoteCounts counts = new VoteCounts(aggregate.recommended(), aggregate.mustSee(), aggregate.notForMe());
    String myVote = aggregate.myVote() == null ? null : aggregate.myVote().apiValue();
    ContentOrigin origin = detectOrigin(item);
    return new ContentItemResponse(
        item.id(),
        item.title(),
        item.url(),
        item.summary(),
        item.source(),
        item.thumbnailUrl(),
        origin.apiValue,
        item.createdAt(),
        item.publishedAt(),
        item.tags(),
        item.author(),
        CommunityContentMedia.normalizeItemType(item.mediaType()),
        counts,
        myVote,
        score);
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

  private String normalizeMediaFilter(String raw) {
    return CommunityContentMedia.normalizeFilter(raw);
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

  public record VoteRequest(String vote) {
  }

  public record VoteResponse(ContentItemResponse item) {
  }

  public record ContentDetailResponse(ContentItemResponse item) {
  }

  public record ContentListResponse(
      String view,
      String filter,
      String media,
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
      @JsonProperty("thumbnail_url") String thumbnailUrl,
      String origin,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("published_at") Instant publishedAt,
      List<String> tags,
      String author,
      @JsonProperty("media_type") String mediaType,
      @JsonProperty("vote_counts") VoteCounts voteCounts,
      @JsonProperty("my_vote") String myVote,
      double score) {
  }

  public record VoteCounts(
      long recommended, @JsonProperty("must_see") long mustSee, @JsonProperty("not_for_me") long notForMe) {
  }

  private enum ContentFilter {
    ALL("all"),
    INTERNET("internet"),
    MEMBERS("members");

    final String apiValue;

    ContentFilter(String apiValue) {
      this.apiValue = apiValue;
    }
  }

  private enum ContentOrigin {
    INTERNET("internet"),
    MEMBERS("members");

    final String apiValue;

    ContentOrigin(String apiValue) {
      this.apiValue = apiValue;
    }
  }
}
