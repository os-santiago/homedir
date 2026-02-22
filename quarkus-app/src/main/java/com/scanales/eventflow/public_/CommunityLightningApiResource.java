package com.scanales.eventflow.public_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.eventflow.community.CommunityLightningComment;
import com.scanales.eventflow.community.CommunityLightningService;
import com.scanales.eventflow.community.CommunityLightningThread;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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
import org.jboss.logging.Logger;

@Path("/api/community/lightning")
@Produces(MediaType.APPLICATION_JSON)
public class CommunityLightningApiResource {
  private static final Logger LOG = Logger.getLogger(CommunityLightningApiResource.class);

  @Inject CommunityLightningService lightningService;
  @Inject UsageMetricsService metrics;
  @Inject GamificationService gamificationService;
  @Inject SecurityIdentity identity;

  @GET
  public Response list(
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam,
      @QueryParam("comments_limit") Integer commentsLimitParam) {
    int limit = clamp(limitParam, 10, 30);
    int offset = Math.max(0, offsetParam == null ? 0 : offsetParam);
    int commentsLimit = clamp(commentsLimitParam, 3, 8);
    CommunityLightningService.FeedPage page = lightningService.listPublished(limit, offset);
    List<String> ids = page.items().stream().map(CommunityLightningThread::id).toList();
    Map<String, List<CommunityLightningComment>> commentsByThread =
        lightningService.listCommentsForThreads(ids, commentsLimit);
    Map<String, Instant> lastCommentAt = lightningService.lastCommentAtByThread(ids);
    String viewerUserId = currentUserId().orElse(null);
    List<ThreadItemResponse> items =
        page.items().stream()
            .map(
                item ->
                    toThreadItem(
                        item,
                        commentsByThread.get(item.id()),
                        viewerUserId,
                        lastCommentAt.get(item.id())))
            .toList();
    return Response.ok(
            new ThreadListResponse(
                page.limit(),
                page.offset(),
                page.total(),
                page.queueDepth(),
                page.nextPublishAt(),
                items))
        .build();
  }

  @POST
  @Path("/threads")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createThread(CreateThreadRequest request) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      CommunityLightningService.CreateThreadResult result =
          lightningService.createThread(
              userId.get(),
              currentUserName().orElse(userId.get()),
              new CommunityLightningService.CreateThreadRequest(
                  null,
                  request != null ? request.effectiveStatement() : null,
                  request != null ? request.effectiveStatement() : null));
      metrics.recordFunnelStep("community.lightning.thread.create");
      metrics.recordFunnelStep("community_lightning_post");
      gamificationService.award(userId.get(), GamificationActivity.LTA_THREAD_CREATE);
      ThreadItemResponse item = toThreadItem(result.item(), List.of(), userId.get(), null);
      return Response.status(Response.Status.CREATED)
          .entity(
              new ThreadMutationResponse(
                  item, result.queued(), result.queuePosition(), result.nextPublishAt(), null))
          .build();
    } catch (CommunityLightningService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunityLightningService.RateLimitExceededException e) {
      return Response.status(429)
          .entity(Map.of("error", e.getMessage(), "message", e.userMessage()))
          .build();
    } catch (Exception e) {
      LOG.errorf(e, "community_lightning_create_failed user=%s", userId.get());
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "lightning_storage_unavailable"))
          .build();
    }
  }

  @PUT
  @Path("/threads/{id}")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response editThread(@PathParam("id") String threadId, EditThreadRequest request) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      String statement = request != null ? request.effectiveStatement() : null;
      CommunityLightningThread thread = lightningService.editThread(userId.get(), threadId, statement);
      List<CommunityLightningComment> comments =
          lightningService
              .listCommentsForThreads(List.of(thread.id()), 3)
              .getOrDefault(thread.id(), List.of());
      Instant lastCommentAt =
          lightningService.lastCommentAtByThread(List.of(thread.id())).get(thread.id());
      return Response.ok(
              new ThreadMutationResponse(
                  toThreadItem(thread, comments, userId.get(), lastCommentAt), false, 0, null, null))
          .build();
    } catch (CommunityLightningService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunityLightningService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunityLightningService.ForbiddenException e) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", e.getMessage())).build();
    } catch (Exception e) {
      LOG.errorf(e, "community_lightning_edit_thread_failed thread=%s", threadId);
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "lightning_storage_unavailable"))
          .build();
    }
  }

  @POST
  @Path("/threads/{id}/comments")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response addComment(@PathParam("id") String threadId, CommentRequest request) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      CommunityLightningService.CommentResult result =
          lightningService.addComment(
              userId.get(), currentUserName().orElse(userId.get()), threadId, request != null ? request.body() : null);
      metrics.recordFunnelStep("community.lightning.comment.create");
      metrics.recordFunnelStep("community_lightning_comment");
      gamificationService.award(userId.get(), GamificationActivity.LTA_COMMENT_CREATE);
      return Response.ok(
              new CommentMutationResponse(
                  toThreadItem(
                      result.thread(),
                      lightningService
                          .listCommentsForThreads(List.of(result.thread().id()), 3)
                          .getOrDefault(result.thread().id(), List.of()),
                      userId.get(),
                      lightningService
                          .lastCommentAtByThread(List.of(result.thread().id()))
                          .get(result.thread().id())),
                  toCommentItem(result.comment(), userId.get())))
          .build();
    } catch (CommunityLightningService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunityLightningService.RateLimitExceededException e) {
      return Response.status(429)
          .entity(Map.of("error", e.getMessage(), "message", e.userMessage()))
          .build();
    } catch (CommunityLightningService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    } catch (Exception e) {
      LOG.errorf(e, "community_lightning_comment_failed thread=%s", threadId);
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "lightning_storage_unavailable"))
          .build();
    }
  }

  @PUT
  @Path("/comments/{id}")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response editComment(@PathParam("id") String commentId, EditCommentRequest request) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      CommunityLightningService.CommentResult result =
          lightningService.editComment(userId.get(), commentId, request != null ? request.body() : null);
      List<CommunityLightningComment> comments =
          lightningService
              .listCommentsForThreads(List.of(result.thread().id()), 3)
              .getOrDefault(result.thread().id(), List.of());
      Instant lastCommentAt =
          lightningService.lastCommentAtByThread(List.of(result.thread().id())).get(result.thread().id());
      return Response.ok(
              new CommentMutationResponse(
                  toThreadItem(result.thread(), comments, userId.get(), lastCommentAt),
                  toCommentItem(result.comment(), userId.get())))
          .build();
    } catch (CommunityLightningService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunityLightningService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunityLightningService.ForbiddenException e) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", e.getMessage())).build();
    } catch (Exception e) {
      LOG.errorf(e, "community_lightning_edit_comment_failed comment=%s", commentId);
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "lightning_storage_unavailable"))
          .build();
    }
  }

  @PUT
  @Path("/threads/{id}/like")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response likeThread(@PathParam("id") String threadId, LikeRequest request) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      boolean liked = request == null || request.liked() == null || request.liked();
      CommunityLightningService.LikeResult result =
          lightningService.setThreadLiked(userId.get(), threadId, liked);
      metrics.recordFunnelStep("community.lightning.thread.like");
      if (liked) {
        gamificationService.award(userId.get(), GamificationActivity.LTA_REACTION);
      }
      return Response.ok(
              new LikeMutationResponse(
                  toThreadItem(result.thread(), List.of(), userId.get(), null),
                  null,
                  liked))
          .build();
    } catch (CommunityLightningService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunityLightningService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @PUT
  @Path("/comments/{id}/like")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response likeComment(@PathParam("id") String commentId, LikeRequest request) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      boolean liked = request == null || request.liked() == null || request.liked();
      CommunityLightningService.LikeResult result =
          lightningService.setCommentLiked(userId.get(), commentId, liked);
      metrics.recordFunnelStep("community.lightning.comment.like");
      if (liked) {
        gamificationService.award(userId.get(), GamificationActivity.LTA_REACTION);
      }
      return Response.ok(
              new LikeMutationResponse(
                  result.thread() == null
                      ? null
                      : toThreadItem(result.thread(), List.of(), userId.get(), null),
                  result.comment() == null ? null : toCommentItem(result.comment(), userId.get()),
                  liked))
          .build();
    } catch (CommunityLightningService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunityLightningService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @POST
  @Path("/threads/{id}/report")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response reportThread(@PathParam("id") String threadId, ReportRequest request) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      CommunityLightningService.ReportResult report =
          lightningService.reportThread(
              userId.get(),
              currentUserName().orElse(userId.get()),
              threadId,
              request != null ? request.reason() : null);
      metrics.recordFunnelStep("community.lightning.report");
      return Response.ok(new ReportMutationResponse(report.reportId(), report.duplicate(), report.totalReports()))
          .build();
    } catch (CommunityLightningService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunityLightningService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @POST
  @Path("/comments/{id}/report")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response reportComment(@PathParam("id") String commentId, ReportRequest request) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      CommunityLightningService.ReportResult report =
          lightningService.reportComment(
              userId.get(),
              currentUserName().orElse(userId.get()),
              commentId,
              request != null ? request.reason() : null);
      metrics.recordFunnelStep("community.lightning.report");
      return Response.ok(new ReportMutationResponse(report.reportId(), report.duplicate(), report.totalReports()))
          .build();
    } catch (CommunityLightningService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunityLightningService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  private ThreadItemResponse toThreadItem(
      CommunityLightningThread item,
      List<CommunityLightningComment> comments,
      String viewerUserId,
      Instant lastCommentAt) {
    List<CommentItemResponse> commentItems =
        comments == null ? List.of() : comments.stream().map(comment -> toCommentItem(comment, viewerUserId)).toList();
    CommentItemResponse best =
        commentItems.stream()
            .filter(comment -> item.bestCommentId() != null && item.bestCommentId().equals(comment.id()))
            .findFirst()
            .orElse(commentItems.isEmpty() ? null : commentItems.getFirst());
    return new ThreadItemResponse(
        item.id(),
        item.mode(),
        item.title(),
        item.body(),
        item.userName(),
        item.createdAt(),
        item.updatedAt(),
        item.publishedAt(),
        lastCommentAt,
        viewerUserId != null && viewerUserId.equalsIgnoreCase(item.userId()),
        item.likes(),
        item.comments(),
        item.reports(),
        item.bestCommentId(),
        best,
        commentItems);
  }

  private CommentItemResponse toCommentItem(CommunityLightningComment comment, String viewerUserId) {
    return new CommentItemResponse(
        comment.id(),
        comment.threadId(),
        comment.body(),
        comment.userName(),
        comment.createdAt(),
        comment.updatedAt(),
        viewerUserId != null && viewerUserId.equalsIgnoreCase(comment.userId()),
        comment.likes(),
        comment.reports());
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

  private Optional<String> currentUserName() {
    if (identity == null || identity.isAnonymous()) {
      return Optional.empty();
    }
    String name = AdminUtils.getClaim(identity, "name");
    if (name != null && !name.isBlank()) {
      return Optional.of(name);
    }
    return currentUserId();
  }

  private int clamp(Integer value, int fallback, int max) {
    if (value == null || value <= 0) {
      return fallback;
    }
    return Math.min(value, max);
  }

  public record CreateThreadRequest(String statement, String title, String body) {
    public String effectiveStatement() {
      if (statement != null && !statement.isBlank()) {
        return statement;
      }
      if (title != null && !title.isBlank()) {
        return title;
      }
      return body;
    }
  }

  public record EditThreadRequest(String statement, String title, String body) {
    public String effectiveStatement() {
      if (statement != null && !statement.isBlank()) {
        return statement;
      }
      if (title != null && !title.isBlank()) {
        return title;
      }
      return body;
    }
  }

  public record CommentRequest(String body) {}

  public record EditCommentRequest(String body) {}

  public record LikeRequest(Boolean liked) {}

  public record ReportRequest(String reason) {}

  public record ThreadListResponse(
      int limit,
      int offset,
      int total,
      @JsonProperty("queue_depth") int queueDepth,
      @JsonProperty("next_publish_at") Instant nextPublishAt,
      List<ThreadItemResponse> items) {}

  public record ThreadMutationResponse(
      ThreadItemResponse item,
      boolean queued,
      @JsonProperty("queue_position") int queuePosition,
      @JsonProperty("next_publish_at") Instant nextPublishAt,
      String message) {}

  public record CommentMutationResponse(ThreadItemResponse thread, CommentItemResponse comment) {}

  public record LikeMutationResponse(ThreadItemResponse thread, CommentItemResponse comment, boolean liked) {}

  public record ReportMutationResponse(String reportId, boolean duplicate, int reports) {}

  public record ThreadItemResponse(
      String id,
      String mode,
      String title,
      String body,
      @JsonProperty("user_name") String userName,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("updated_at") Instant updatedAt,
      @JsonProperty("published_at") Instant publishedAt,
      @JsonProperty("last_comment_at") Instant lastCommentAt,
      @JsonProperty("is_owner") boolean isOwner,
      int likes,
      int comments,
      int reports,
      @JsonProperty("best_comment_id") String bestCommentId,
      @JsonProperty("best_comment") CommentItemResponse bestComment,
      @JsonProperty("top_comments") List<CommentItemResponse> topComments) {}

  public record CommentItemResponse(
      String id,
      @JsonProperty("thread_id") String threadId,
      String body,
      @JsonProperty("user_name") String userName,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("updated_at") Instant updatedAt,
      @JsonProperty("is_owner") boolean isOwner,
      int likes,
      int reports) {}
}
