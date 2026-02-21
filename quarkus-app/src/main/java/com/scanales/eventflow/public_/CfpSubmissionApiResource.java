package com.scanales.eventflow.public_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.eventflow.cfp.CfpSubmission;
import com.scanales.eventflow.cfp.CfpConfig;
import com.scanales.eventflow.cfp.CfpConfigService;
import com.scanales.eventflow.cfp.CfpSubmissionService;
import com.scanales.eventflow.cfp.CfpSubmissionStatus;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.PersistenceService;
import com.scanales.eventflow.service.SpeakerService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.util.AdminUtils;
import com.scanales.eventflow.util.PaginationGuardrails;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Path("/api/events/{eventId}/cfp/submissions")
@Produces(MediaType.APPLICATION_JSON)
public class CfpSubmissionApiResource {
  private static final int DEFAULT_LIMIT = PaginationGuardrails.DEFAULT_PAGE_LIMIT;
  private static final int MAX_LIMIT = PaginationGuardrails.MAX_PAGE_LIMIT;
  private static final int MAX_OFFSET = PaginationGuardrails.MAX_OFFSET;

  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject CfpConfigService cfpConfigService;
  @Inject PersistenceService persistenceService;
  @Inject SpeakerService speakerService;
  @Inject UsageMetricsService metrics;
  @Inject GamificationService gamificationService;
  @Inject SecurityIdentity identity;

  @POST
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response create(@PathParam("eventId") String eventId, CreateSubmissionRequest request) {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    String primaryUserId = userIds.iterator().next();
    try {
      CfpSubmission submission =
          cfpSubmissionService.create(
              primaryUserId,
              currentUserName().orElse(primaryUserId),
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
      metrics.recordFunnelStep("cfp.submission.create");
      metrics.recordFunnelStep("cfp_submit");
      gamificationService.award(primaryUserId, GamificationActivity.CFP_SUBMIT, eventId);
      return Response.status(Response.Status.CREATED).entity(new SubmissionResponse(toView(submission))).build();
    } catch (CfpSubmissionService.ValidationException e) {
      Response.Status status =
          ("proposal_limit_reached".equals(e.getMessage()) || "duplicate_title".equals(e.getMessage()))
              ? Response.Status.CONFLICT
              : Response.Status.BAD_REQUEST;
      return Response.status(status).entity(Map.of("error", e.getMessage())).build();
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
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    int limit = normalizeLimit(limitParam);
    int offset = PaginationGuardrails.clampOffset(offsetParam, MAX_OFFSET);
    List<SubmissionView> items =
        cfpSubmissionService.listMine(eventId, userIds, limit, offset).stream().map(this::toView).toList();
    return Response.ok(new SubmissionListResponse(limit, offset, items)).build();
  }
  @GET
  @Path("/config")
  public Response submissionConfig(@PathParam("eventId") String eventId) {
    CfpConfig config = cfpConfigService.current();
    int limit = config.maxSubmissionsPerUserPerEvent();
    return Response.ok(
            new SubmissionLimitConfigResponse(
                limit,
                CfpSubmissionService.MIN_SUBMISSIONS_PER_USER_PER_EVENT,
                CfpSubmissionService.MAX_SUBMISSIONS_PER_USER_PER_EVENT,
                AdminUtils.isAdmin(identity),
                config.testingModeEnabled()))
        .build();
  }

  @GET
  @Path("/storage")
  @Authenticated
  public Response storage() {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    PersistenceService.CfpStorageInfo info = persistenceService.cfpStorageInfo();
    return Response.ok(
            new StorageHealthResponse(
                info.primaryPath(),
                info.backupsPath(),
                info.primaryExists(),
                info.primarySizeBytes(),
                info.primaryLastModifiedMillis(),
                info.backupCount(),
                info.latestBackupName(),
                info.latestBackupSizeBytes(),
                info.latestBackupLastModifiedMillis(),
                info.primaryValid(),
                info.primaryMissingChecksum(),
                info.primaryValidationError(),
                info.backupValidCount(),
                info.backupInvalidCount(),
                info.backupMissingChecksumCount(),
                info.latestBackupValid(),
                info.walEnabled(),
                info.walPath(),
                info.walSizeBytes(),
                info.walLastModifiedMillis(),
                info.walAppends(),
                info.walCompactions(),
                info.walRecoveries(),
                info.checksumEnabled(),
                info.checksumRequired(),
                info.checksumMismatches(),
                info.checksumHydrations()))
        .build();
  }

  @POST
  @Path("/storage/repair")
  @Authenticated
  public Response repairStorage(
      @PathParam("eventId") String eventId,
      @QueryParam("dry_run") Boolean dryRunSnake,
      @QueryParam("dryRun") Boolean dryRunCamel) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    boolean dryRun = dryRunSnake != null ? dryRunSnake : (dryRunCamel != null ? dryRunCamel : true);
    PersistenceService.CfpStorageRepairReport report = persistenceService.repairCfpStorage(dryRun);
    return Response.ok(
            new StorageRepairResponse(
                report.dryRun(),
                report.primaryExists(),
                report.primaryValid(),
                report.primaryMissingChecksum(),
                report.primaryRepaired(),
                report.primaryQuarantined(),
                report.backupsScanned(),
                report.backupsValid(),
                report.backupsNeedingRepair(),
                report.backupsRepaired(),
                report.backupsQuarantineCandidates(),
                report.backupsQuarantined(),
                report.errors(),
                report.errorDetails()))
        .build();
  }

  @PUT
  @Path("/config")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateSubmissionConfig(
      @PathParam("eventId") String eventId, SubmissionLimitConfigUpdateRequest request) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    Integer requestedLimit = request != null ? request.maxPerUser() : null;
    Boolean requestedTesting = request != null ? request.testingModeEnabled() : null;
    if (requestedLimit == null && requestedTesting == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_config")).build();
    }
    if (requestedLimit != null
        && (requestedLimit < CfpSubmissionService.MIN_SUBMISSIONS_PER_USER_PER_EVENT
            || requestedLimit > CfpSubmissionService.MAX_SUBMISSIONS_PER_USER_PER_EVENT)) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              Map.of(
                  "error", "invalid_limit",
                  "min", CfpSubmissionService.MIN_SUBMISSIONS_PER_USER_PER_EVENT,
                  "max", CfpSubmissionService.MAX_SUBMISSIONS_PER_USER_PER_EVENT))
          .build();
    }
    CfpConfig updated = cfpConfigService.update(requestedLimit, requestedTesting);
    return Response.ok(
            new SubmissionLimitConfigResponse(
                updated.maxSubmissionsPerUserPerEvent(),
                CfpSubmissionService.MIN_SUBMISSIONS_PER_USER_PER_EVENT,
                CfpSubmissionService.MAX_SUBMISSIONS_PER_USER_PER_EVENT,
                true,
                updated.testingModeEnabled()))
        .build();
  }
  @DELETE
  @Path("/{id}")
  @Authenticated
  public Response deleteMine(@PathParam("eventId") String eventId, @PathParam("id") String id) {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    Optional<CfpSubmission> existing = cfpSubmissionService.findById(id);
    if (existing.isEmpty() || !eventId.equals(existing.get().eventId())) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "submission_not_found")).build();
    }
    boolean admin = AdminUtils.isAdmin(identity);
    if (!admin && !userIds.contains(existing.get().proposerUserId())) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "owner_required")).build();
    }
    try {
      CfpSubmission deleted = cfpSubmissionService.delete(eventId, id);
      return Response.ok(new SubmissionResponse(toView(deleted))).build();
    } catch (CfpSubmissionService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    } catch (IllegalStateException e) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "cfp_storage_unavailable", "detail", storageDetail(e)))
          .build();
    }
  }

  @GET
  @Authenticated
  public Response listForModeration(
      @PathParam("eventId") String eventId,
      @QueryParam("status") String status,
      @QueryParam("sort") String sort,
      @QueryParam("limit") Integer limitParam,
      @QueryParam("offset") Integer offsetParam) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    Optional<CfpSubmissionStatus> statusFilter = parseStatusFilter(status);
    if (statusFilter == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_status")).build();
    }
    CfpSubmissionService.SortOrder sortOrder = parseSortOrder(sort);
    if (sortOrder == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_sort")).build();
    }
    int limit = normalizeLimit(limitParam);
    int offset = PaginationGuardrails.clampOffset(offsetParam, MAX_OFFSET);
    List<SubmissionView> items =
        cfpSubmissionService.listByEvent(eventId, statusFilter, sortOrder, limit, offset).stream()
            .map(this::toView)
            .toList();
    return Response.ok(new SubmissionListResponse(limit, offset, items)).build();
  }

  @GET
  @Path("/export.csv")
  @Authenticated
  @Produces("text/csv")
  public Response exportModerationCsv(
      @PathParam("eventId") String eventId,
      @QueryParam("status") String status,
      @QueryParam("sort") String sort) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    Optional<CfpSubmissionStatus> statusFilter = parseStatusFilter(status);
    if (statusFilter == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_status")).build();
    }
    CfpSubmissionService.SortOrder sortOrder = parseSortOrder(sort);
    if (sortOrder == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_sort")).build();
    }

    List<CfpSubmission> items = cfpSubmissionService.listByEventAll(eventId, statusFilter, sortOrder);
    String csv = buildCsv(items);
    String filename = "cfp-" + sanitizeIdToken(eventId, 30) + "-report.csv";
    return Response.ok(csv)
        .type("text/csv; charset=utf-8")
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .build();
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
      metrics.recordFunnelStep("cfp.submission.status");
      metrics.recordFunnelStep("cfp.submission.status." + status.get().apiValue());
      if (status.get() == CfpSubmissionStatus.ACCEPTED) {
        metrics.recordFunnelStep("cfp_approved");
        gamificationService.award(updated.proposerUserId(), GamificationActivity.CFP_ACCEPTED, updated.id());
      }
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

  @PUT
  @Path("/{id}/rating")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateRating(
      @PathParam("eventId") String eventId,
      @PathParam("id") String id,
      UpdateRatingRequest request) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    if (request == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "rating_required")).build();
    }
    try {
      CfpSubmission updated =
          cfpSubmissionService.updateRating(
              eventId,
              id,
              request.technicalDetail(),
              request.narrative(),
              request.contentImpact(),
              currentUserId().orElse("admin"));
      return Response.ok(new SubmissionResponse(toView(updated))).build();
    } catch (CfpSubmissionService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
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
        submission.moderationNote(),
        submission.ratingTechnicalDetail(),
        submission.ratingNarrative(),
        submission.ratingContentImpact(),
        CfpSubmissionService.calculateWeightedScore(submission));
  }

  private Optional<String> currentUserId() {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(userIds.iterator().next());
  }

  private Set<String> currentUserIds() {
    if (identity == null || identity.isAnonymous()) {
      return Set.of();
    }
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    addNormalizedUserId(ids, principal);
    addNormalizedUserId(ids, AdminUtils.getClaim(identity, "email"));
    addNormalizedUserId(ids, AdminUtils.getClaim(identity, "sub"));
    return ids.isEmpty() ? Set.of() : Collections.unmodifiableSet(ids);
  }

  private static void addNormalizedUserId(Set<String> target, String raw) {
    if (raw == null || raw.isBlank()) {
      return;
    }
    target.add(raw.trim().toLowerCase(Locale.ROOT));
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

  private static Optional<CfpSubmissionStatus> parseStatusFilter(String status) {
    if (status == null || status.isBlank()) {
      return Optional.of(CfpSubmissionStatus.PENDING);
    }
    if ("all".equalsIgnoreCase(status.trim())) {
      return Optional.empty();
    }
    Optional<CfpSubmissionStatus> parsed = CfpSubmissionStatus.fromApi(status);
    return parsed.isPresent() ? parsed : null;
  }

  private static CfpSubmissionService.SortOrder parseSortOrder(String sort) {
    if (sort == null || sort.isBlank() || "created".equalsIgnoreCase(sort) || "recent".equalsIgnoreCase(sort)) {
      return CfpSubmissionService.SortOrder.CREATED_DESC;
    }
    if ("score".equalsIgnoreCase(sort) || "weighted".equalsIgnoreCase(sort)) {
      return CfpSubmissionService.SortOrder.SCORE_DESC;
    }
    return null;
  }

  private static String buildCsv(List<CfpSubmission> items) {
    StringBuilder csv = new StringBuilder();
    csv.append(
            "id,event_id,title,status,proposer_user_id,proposer_name,created_at,level,format,duration_min,language,track,"
                + "rating_technical_detail,rating_narrative,rating_content_impact,rating_weighted,moderation_note,links")
        .append('\n');

    for (CfpSubmission item : items) {
      Double weighted = CfpSubmissionService.calculateWeightedScore(item);
      csv.append(csvValue(item.id())).append(',')
          .append(csvValue(item.eventId())).append(',')
          .append(csvValue(item.title())).append(',')
          .append(csvValue(item.status() != null ? item.status().apiValue() : null)).append(',')
          .append(csvValue(item.proposerUserId())).append(',')
          .append(csvValue(item.proposerName())).append(',')
          .append(csvValue(item.createdAt() != null ? item.createdAt().toString() : null)).append(',')
          .append(csvValue(item.level())).append(',')
          .append(csvValue(item.format())).append(',')
          .append(csvValue(item.durationMin())).append(',')
          .append(csvValue(item.language())).append(',')
          .append(csvValue(item.track())).append(',')
          .append(csvValue(item.ratingTechnicalDetail())).append(',')
          .append(csvValue(item.ratingNarrative())).append(',')
          .append(csvValue(item.ratingContentImpact())).append(',')
          .append(csvValue(weighted)).append(',')
          .append(csvValue(item.moderationNote())).append(',')
          .append(csvValue(item.links() == null ? null : String.join(" | ", item.links())))
          .append('\n');
    }
    return csv.toString();
  }

  private static String csvValue(Object value) {
    if (value == null) {
      return "";
    }
    String raw = String.valueOf(value).replace("\r", " ").replace("\n", " ").trim();
    if (raw.contains(",") || raw.contains("\"") || raw.contains(";")) {
      return '"' + raw.replace("\"", "\"\"") + '"';
    }
    return raw;
  }
  private static String storageDetail(IllegalStateException e) {
    if (e == null || e.getMessage() == null || e.getMessage().isBlank()) {
      return "unknown";
    }
    return e.getMessage();
  }

  private static int normalizeLimit(Integer rawLimit) {
    return PaginationGuardrails.clampLimit(rawLimit, DEFAULT_LIMIT, MAX_LIMIT);
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

  public record UpdateRatingRequest(
      @JsonProperty("technical_detail") Integer technicalDetail,
      Integer narrative,
      @JsonProperty("content_impact") Integer contentImpact) {}

  public record PromoteResponse(
      @JsonProperty("speaker_id") String speakerId,
      @JsonProperty("talk_id") String talkId,
      @JsonProperty("created_speaker") boolean createdSpeaker,
      @JsonProperty("created_talk") boolean createdTalk) {}

  public record SubmissionResponse(SubmissionView item) {}

  public record SubmissionListResponse(int limit, int offset, List<SubmissionView> items) {}
  public record SubmissionLimitConfigUpdateRequest(
      @JsonProperty("max_per_user") Integer maxPerUser,
      @JsonProperty("testing_mode_enabled") Boolean testingModeEnabled) {}

  public record StorageHealthResponse(
      @JsonProperty("primary_path") String primaryPath,
      @JsonProperty("backups_path") String backupsPath,
      @JsonProperty("primary_exists") boolean primaryExists,
      @JsonProperty("primary_size_bytes") long primarySizeBytes,
      @JsonProperty("primary_last_modified_ms") long primaryLastModifiedMillis,
      @JsonProperty("backup_count") int backupCount,
      @JsonProperty("latest_backup_name") String latestBackupName,
      @JsonProperty("latest_backup_size_bytes") long latestBackupSizeBytes,
      @JsonProperty("latest_backup_last_modified_ms") long latestBackupLastModifiedMillis,
      @JsonProperty("primary_valid") boolean primaryValid,
      @JsonProperty("primary_missing_checksum") boolean primaryMissingChecksum,
      @JsonProperty("primary_validation_error") String primaryValidationError,
      @JsonProperty("backup_valid_count") int backupValidCount,
      @JsonProperty("backup_invalid_count") int backupInvalidCount,
      @JsonProperty("backup_missing_checksum_count") int backupMissingChecksumCount,
      @JsonProperty("latest_backup_valid") Boolean latestBackupValid,
      @JsonProperty("wal_enabled") boolean walEnabled,
      @JsonProperty("wal_path") String walPath,
      @JsonProperty("wal_size_bytes") long walSizeBytes,
      @JsonProperty("wal_last_modified_ms") long walLastModifiedMillis,
      @JsonProperty("wal_appends") long walAppends,
      @JsonProperty("wal_compactions") long walCompactions,
      @JsonProperty("wal_recoveries") long walRecoveries,
      @JsonProperty("checksum_enabled") boolean checksumEnabled,
      @JsonProperty("checksum_required") boolean checksumRequired,
      @JsonProperty("checksum_mismatches") long checksumMismatches,
      @JsonProperty("checksum_hydrations") long checksumHydrations) {}

  public record StorageRepairResponse(
      @JsonProperty("dry_run") boolean dryRun,
      @JsonProperty("primary_exists") boolean primaryExists,
      @JsonProperty("primary_valid") boolean primaryValid,
      @JsonProperty("primary_missing_checksum") boolean primaryMissingChecksum,
      @JsonProperty("primary_repaired") boolean primaryRepaired,
      @JsonProperty("primary_quarantined") boolean primaryQuarantined,
      @JsonProperty("backups_scanned") int backupsScanned,
      @JsonProperty("backups_valid") int backupsValid,
      @JsonProperty("backups_needing_repair") int backupsNeedingRepair,
      @JsonProperty("backups_repaired") int backupsRepaired,
      @JsonProperty("backups_quarantine_candidates") int backupsQuarantineCandidates,
      @JsonProperty("backups_quarantined") int backupsQuarantined,
      int errors,
      @JsonProperty("error_details") List<String> errorDetails) {}

  public record SubmissionLimitConfigResponse(
      @JsonProperty("max_per_user") int maxPerUser,
      @JsonProperty("min_allowed") int minAllowed,
      @JsonProperty("max_allowed") int maxAllowed,
      boolean admin,
      @JsonProperty("testing_mode_enabled") boolean testingModeEnabled) {}

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
      @JsonProperty("moderation_note") String moderationNote,
      @JsonProperty("rating_technical_detail") Integer ratingTechnicalDetail,
      @JsonProperty("rating_narrative") Integer ratingNarrative,
      @JsonProperty("rating_content_impact") Integer ratingContentImpact,
      @JsonProperty("rating_weighted") Double ratingWeighted) {}
}
