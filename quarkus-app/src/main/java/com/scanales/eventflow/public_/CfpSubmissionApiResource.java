package com.scanales.eventflow.public_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.eventflow.cfp.CfpSubmission;
import com.scanales.eventflow.cfp.CfpSubmissionService;
import com.scanales.eventflow.cfp.CfpSubmissionStatus;
import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.SpeakerService;
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

@Path("/api/events/{eventId}/cfp/submissions")
@Produces(MediaType.APPLICATION_JSON)
public class CfpSubmissionApiResource {

  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject SpeakerService speakerService;
  @Inject SecurityIdentity identity;

  @POST
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response create(@PathParam("eventId") String eventId, CreateSubmissionRequest request) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    try {
      CfpSubmission submission =
          cfpSubmissionService.create(
              userId.get(),
              currentUserName().orElse(userId.get()),
              new CfpSubmissionService.CreateRequest(
                  eventId,
                  request != null ? request.title() : null,
                  request != null ? request.summary() : null,
                  request != null ? request.abstractText() : null,
                  request != null ? request.level() : null,
                  request != null ? request.format() : null,
                  request != null ? request.durationMin() : null,
                  request != null ? request.language() : null,
                  request != null ? request.track() : null,
                  request != null ? request.tags() : null,
                  request != null ? request.links() : null));
      return Response.status(Response.Status.CREATED).entity(new SubmissionResponse(toView(submission))).build();
    } catch (CfpSubmissionService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (CfpSubmissionService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    } catch (IllegalStateException e) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "cfp_storage_unavailable", "detail", storageDetail(e)))
          .build();
    }
  }

  @GET
  @Path("/mine")
  @Authenticated
  public Response mine(
      @PathParam("eventId") String eventId,
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    Optional<String> userId = currentUserId();
    if (userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    int limit = normalizeLimit(limitParam);
    int offset = Math.max(0, offsetParam == null ? 0 : offsetParam);
    List<SubmissionView> items =
        cfpSubmissionService.listMine(eventId, userId.get(), limit, offset).stream().map(this::toView).toList();
    return Response.ok(new SubmissionListResponse(limit, offset, items)).build();
  }

  @GET
  @Authenticated
  public Response listForModeration(
      @PathParam("eventId") String eventId,
      @QueryParam("status") String status,
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    Optional<CfpSubmissionStatus> statusFilter;
    if (status == null || status.isBlank()) {
      statusFilter = Optional.of(CfpSubmissionStatus.PENDING);
    } else {
      statusFilter = CfpSubmissionStatus.fromApi(status);
      if (statusFilter.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_status")).build();
      }
    }
    int limit = normalizeLimit(limitParam);
    int offset = Math.max(0, offsetParam == null ? 0 : offsetParam);
    List<SubmissionView> items =
        cfpSubmissionService.listByEvent(eventId, statusFilter, limit, offset).stream().map(this::toView).toList();
    return Response.ok(new SubmissionListResponse(limit, offset, items)).build();
  }

  @PUT
  @Path("/{id}/status")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateStatus(
      @PathParam("eventId") String eventId,
      @PathParam("id") String id,
      UpdateStatusRequest request) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    Optional<CfpSubmissionStatus> status =
        CfpSubmissionStatus.fromApi(request != null ? request.status() : null);
    if (status.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_status")).build();
    }
    try {
      Optional<CfpSubmission> existing = cfpSubmissionService.findById(id);
      if (existing.isEmpty() || !eventId.equals(existing.get().eventId())) {
        return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "submission_not_found")).build();
      }
      CfpSubmission updated =
          cfpSubmissionService.updateStatus(
              id, status.get(), currentUserId().orElse("admin"), request != null ? request.note() : null);
      return Response.ok(new SubmissionResponse(toView(updated))).build();
    } catch (CfpSubmissionService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    } catch (CfpSubmissionService.InvalidTransitionException e) {
      return Response.status(Response.Status.CONFLICT).entity(Map.of("error", e.getMessage())).build();
    } catch (CfpSubmissionService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (IllegalStateException e) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "cfp_storage_unavailable", "detail", storageDetail(e)))
          .build();
    }
  }

  @POST
  @Path("/{id}/promote")
  @Authenticated
  public Response promoteAcceptedSubmission(
      @PathParam("eventId") String eventId, @PathParam("id") String id) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    Optional<CfpSubmission> existing = cfpSubmissionService.findById(id);
    if (existing.isEmpty() || !eventId.equals(existing.get().eventId())) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "submission_not_found")).build();
    }
    CfpSubmission submission = existing.get();
    if (submission.status() != CfpSubmissionStatus.ACCEPTED) {
      return Response.status(Response.Status.CONFLICT).entity(Map.of("error", "submission_not_accepted")).build();
    }

    String speakerId = buildSpeakerId(submission);
    String talkId = buildTalkId(submission);
    String speakerName = buildSpeakerName(submission);

    Speaker speaker = speakerService.getSpeaker(speakerId);
    boolean createdSpeaker = false;
    if (speaker == null) {
      speaker = new Speaker(speakerId, speakerName);
      speaker.setBio("CFP proposer for event " + submission.eventId());
      createdSpeaker = true;
    }
    speaker.setName(speakerName);
    speakerService.saveSpeaker(speaker);

    Talk talk = speakerService.getTalk(speakerId, talkId);
    boolean createdTalk = false;
    if (talk == null) {
      talk = new Talk(talkId, submission.title());
      createdTalk = true;
    }
    talk.setName(submission.title());
    talk.setDescription(buildTalkDescription(submission));
    talk.setDurationMinutes(submission.durationMin() != null ? submission.durationMin() : 30);
    speakerService.saveTalk(speakerId, talk);

    return Response.ok(new PromoteResponse(speakerId, talkId, createdSpeaker, createdTalk)).build();
  }

  private SubmissionView toView(CfpSubmission submission) {
    return new SubmissionView(
        submission.id(),
        submission.eventId(),
        submission.proposerUserId(),
        submission.proposerName(),
        submission.title(),
        submission.summary(),
        submission.abstractText(),
        submission.level(),
        submission.format(),
        submission.durationMin(),
        submission.language(),
        submission.track(),
        submission.tags(),
        submission.links(),
        submission.status().apiValue(),
        submission.createdAt(),
        submission.updatedAt(),
        submission.moderatedAt(),
        submission.moderatedBy(),
        submission.moderationNote());
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

  private static String storageDetail(IllegalStateException e) {
    if (e == null || e.getMessage() == null || e.getMessage().isBlank()) {
      return "unknown";
    }
    return e.getMessage();
  }

  private static int normalizeLimit(Integer rawLimit) {
    if (rawLimit == null || rawLimit <= 0) {
      return 20;
    }
    return Math.min(rawLimit, 100);
  }

  private static String buildSpeakerId(CfpSubmission submission) {
    return "cfp-speaker-" + sanitizeIdToken(submission.id(), 32);
  }

  private static String buildTalkId(CfpSubmission submission) {
    return sanitizeIdToken(submission.eventId(), 18) + "-cfp-" + sanitizeIdToken(submission.id(), 24);
  }

  private static String buildSpeakerName(CfpSubmission submission) {
    String name = sanitizeDisplayText(submission.proposerName());
    if (name != null) {
      return name;
    }
    String fallback = sanitizeDisplayText(submission.proposerUserId());
    return fallback != null ? fallback : "CFP Speaker";
  }

  private static String buildTalkDescription(CfpSubmission submission) {
    StringBuilder description = new StringBuilder();
    if (submission.summary() != null && !submission.summary().isBlank()) {
      description.append(submission.summary().trim());
    }
    if (submission.track() != null && !submission.track().isBlank()) {
      if (!description.isEmpty()) {
        description.append("\n\n");
      }
      description.append("Track: ").append(submission.track());
    }
    if (submission.abstractText() != null && !submission.abstractText().isBlank()) {
      if (!description.isEmpty()) {
        description.append("\n\n");
      }
      description.append(submission.abstractText().trim());
    }
    if (description.isEmpty()) {
      description.append("Promoted from CFP submission ").append(submission.id());
    }
    return description.toString();
  }

  private static String sanitizeIdToken(String raw, int maxLength) {
    if (raw == null) {
      return "item";
    }
    String value = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    value = value.replaceAll("^-+", "").replaceAll("-+$", "");
    if (value.isBlank()) {
      value = "item";
    }
    if (value.length() > maxLength) {
      value = value.substring(0, maxLength).replaceAll("-+$", "");
    }
    return value.isBlank() ? "item" : value;
  }

  private static String sanitizeDisplayText(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim().replaceAll("\\s+", " ");
    return value.isBlank() ? null : value;
  }

  public record CreateSubmissionRequest(
      String title,
      String summary,
      @JsonProperty("abstract_text") String abstractText,
      String level,
      String format,
      @JsonProperty("duration_min") Integer durationMin,
      String language,
      String track,
      List<String> tags,
      List<String> links) {}

  public record UpdateStatusRequest(String status, String note) {}

  public record PromoteResponse(
      @JsonProperty("speaker_id") String speakerId,
      @JsonProperty("talk_id") String talkId,
      @JsonProperty("created_speaker") boolean createdSpeaker,
      @JsonProperty("created_talk") boolean createdTalk) {}

  public record SubmissionResponse(SubmissionView item) {}

  public record SubmissionListResponse(int limit, int offset, List<SubmissionView> items) {}

  public record SubmissionView(
      String id,
      @JsonProperty("event_id") String eventId,
      @JsonProperty("proposer_user_id") String proposerUserId,
      @JsonProperty("proposer_name") String proposerName,
      String title,
      String summary,
      @JsonProperty("abstract_text") String abstractText,
      String level,
      String format,
      @JsonProperty("duration_min") Integer durationMin,
      String language,
      String track,
      List<String> tags,
      List<String> links,
      String status,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("updated_at") Instant updatedAt,
      @JsonProperty("moderated_at") Instant moderatedAt,
      @JsonProperty("moderated_by") String moderatedBy,
      @JsonProperty("moderation_note") String moderationNote) {}
}