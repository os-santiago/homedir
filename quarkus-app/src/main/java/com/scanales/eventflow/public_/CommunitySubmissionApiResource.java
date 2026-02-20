package com.scanales.eventflow.public_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.eventflow.community.CommunitySubmission;
import com.scanales.eventflow.community.CommunitySubmissionService;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jboss.logging.Logger;

@Path("/api/community/submissions")
@Produces(MediaType.APPLICATION_JSON)
public class CommunitySubmissionApiResource {
  private static final Logger LOG = Logger.getLogger(CommunitySubmissionApiResource.class);

  @Inject CommunitySubmissionService submissionService;
  @Inject UsageMetricsService metrics;
  @Inject SecurityIdentity identity;

  @POST
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response create(CreateSubmissionRequest request) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      CommunitySubmission submission =
          submissionService.create(
              userId.get(),
              currentUserName().orElse(userId.get()),
              new CommunitySubmissionService.CreateRequest(
                  request != null ? request.title() : null,
                  request != null ? request.url() : null,
                  request != null ? request.summary() : null,
                  request != null ? request.source() : null,
                  request != null ? request.tags() : null));
      metrics.recordFunnelStep("community.submission.create");
      return Response.status(Response.Status.CREATED).entity(new SubmissionResponse(toView(submission))).build();
    } catch (CommunitySubmissionService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunitySubmissionService.DuplicateSubmissionException e) {
      return Response.status(Response.Status.CONFLICT).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunitySubmissionService.RateLimitExceededException e) {
      return Response.status(429).entity(Map.of("error", e.getMessage())).build();
    } catch (IllegalStateException e) {
      LOG.errorf(e, "community_submission_create_storage_unavailable user=%s", userId.orElse("unknown"));
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "submission_storage_unavailable", "detail", storageDetail(e)))
          .build();
    }
  }

  @GET
  @Path("/mine")
  @Authenticated
  public Response mine(
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    int limit = normalizeLimit(limitParam);
    int offset = Math.max(0, offsetParam == null ? 0 : offsetParam);
    List<SubmissionView> items =
        submissionService.listMine(userId.get(), limit, offset).stream().map(this::toView).toList();
    return Response.ok(new SubmissionListResponse(limit, offset, items)).build();
  }

  @GET
  @Path("/pending")
  @Authenticated
  public Response pending(
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    int limit = normalizeLimit(limitParam);
    int offset = Math.max(0, offsetParam == null ? 0 : offsetParam);
    List<SubmissionView> items =
        submissionService.listPending(limit, offset).stream().map(this::toView).toList();
    return Response.ok(new SubmissionListResponse(limit, offset, items)).build();
  }

  @PUT
  @Path("/{id}/approve")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response approve(@PathParam("id") String id, ModerationRequest request) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    try {
      CommunitySubmission submission =
          submissionService.approve(id, currentUserId().orElse("admin"), request != null ? request.note() : null);
      metrics.recordFunnelStep("community.submission.approve");
      return Response.ok(new SubmissionResponse(toView(submission))).build();
    } catch (CommunitySubmissionService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunitySubmissionService.DuplicateSubmissionException e) {
      return Response.status(Response.Status.CONFLICT).entity(Map.of("error", e.getMessage())).build();
    } catch (CommunitySubmissionService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    } catch (IllegalStateException e) {
      LOG.errorf(e, "community_submission_approve_storage_unavailable id=%s", id);
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "approve_storage_unavailable", "detail", storageDetail(e)))
          .build();
    }
  }

  @PUT
  @Path("/{id}/reject")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response reject(@PathParam("id") String id, ModerationRequest request) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    try {
      CommunitySubmission submission =
          submissionService.reject(id, currentUserId().orElse("admin"), request != null ? request.note() : null);
      metrics.recordFunnelStep("community.submission.reject");
      return Response.ok(new SubmissionResponse(toView(submission))).build();
    } catch (CommunitySubmissionService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    } catch (IllegalStateException e) {
      LOG.errorf(e, "community_submission_reject_storage_unavailable id=%s", id);
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "reject_storage_unavailable", "detail", storageDetail(e)))
          .build();
    }
  }

  private static String storageDetail(IllegalStateException e) {
    if (e == null || e.getMessage() == null || e.getMessage().isBlank()) {
      return "unknown";
    }
    return e.getMessage();
  }

  private int normalizeLimit(Integer rawLimit) {
    if (rawLimit == null || rawLimit <= 0) {
      return 20;
    }
    return Math.min(rawLimit, 100);
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

  private SubmissionView toView(CommunitySubmission submission) {
    return new SubmissionView(
        submission.id(),
        submission.title(),
        submission.url(),
        submission.summary(),
        submission.source(),
        submission.tags(),
        submission.createdAt(),
        submission.status().apiValue(),
        submission.moderatedAt(),
        submission.moderatedBy(),
        submission.moderationNote(),
        submission.contentId());
  }

  public record CreateSubmissionRequest(
      String title,
      String url,
      String summary,
      String source,
      List<String> tags) {}

  public record ModerationRequest(String note) {}

  public record SubmissionResponse(SubmissionView item) {}

  public record SubmissionListResponse(int limit, int offset, List<SubmissionView> items) {}

  public record SubmissionView(
      String id,
      String title,
      String url,
      String summary,
      String source,
      List<String> tags,
      @JsonProperty("created_at") java.time.Instant createdAt,
      String status,
      @JsonProperty("moderated_at") java.time.Instant moderatedAt,
      @JsonProperty("moderated_by") String moderatedBy,
      @JsonProperty("moderation_note") String moderationNote,
      @JsonProperty("content_id") String contentId) {}
}
