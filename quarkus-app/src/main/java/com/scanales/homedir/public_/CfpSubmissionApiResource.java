package com.scanales.homedir.public_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.homedir.cfp.CfpPanelist;
import com.scanales.homedir.cfp.CfpPanelistStatus;
import com.scanales.homedir.cfp.CfpPresentationAsset;
import com.scanales.homedir.cfp.CfpSubmission;
import com.scanales.homedir.cfp.CfpConfigService;
import com.scanales.homedir.cfp.CfpEventConfig;
import com.scanales.homedir.cfp.CfpEventConfigService;
import com.scanales.homedir.cfp.CfpInsightsService;
import com.scanales.homedir.cfp.CfpSubmissionService;
import com.scanales.homedir.cfp.CfpSubmissionStatus;
import com.scanales.homedir.eventops.EventOperationsService;
import com.scanales.homedir.eventops.EventStaffRole;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.model.Speaker;
import com.scanales.homedir.model.Talk;
import com.scanales.homedir.service.GamificationService;
import com.scanales.homedir.service.PersistenceService;
import com.scanales.homedir.service.SpeakerService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.service.UserProfileService;
import com.scanales.homedir.util.AdminUtils;
import com.scanales.homedir.util.PaginationGuardrails;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
  @Inject CfpEventConfigService cfpEventConfigService;
  @Inject PersistenceService persistenceService;
  @Inject SpeakerService speakerService;
  @Inject UsageMetricsService metrics;
  @Inject GamificationService gamificationService;
  @Inject CfpInsightsService cfpInsightsService;
  @Inject SecurityIdentity identity;
  @Inject EventOperationsService eventOperationsService;
  @Inject UserProfileService userProfileService;

  @ConfigProperty(name = "homedir.data.dir", defaultValue = "data")
  String dataDirPath;

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
      userProfileService.activateSpeakerProfile(
          primaryUserId, currentUserName().orElse(primaryUserId), primaryUserId);
      metrics.recordFunnelStep("cfp.submission.create");
      metrics.recordFunnelStep("cfp_submit");
      gamificationService.award(primaryUserId, GamificationActivity.CFP_SUBMIT, eventId);
      cfpInsightsService.recordSubmissionCreated(submission);
      return Response.status(Response.Status.CREATED)
          .entity(new SubmissionResponse(toViewerView(submission, userIds)))
          .build();
    } catch (CfpSubmissionService.ValidationException e) {
      Response.Status status =
          ("proposal_limit_reached".equals(e.getMessage())
                  || "duplicate_title".equals(e.getMessage())
                  || "submissions_closed".equals(e.getMessage()))
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
        cfpSubmissionService.listMine(eventId, userIds, limit, offset).stream()
            .map(item -> safeRefreshPanelists(eventId, item))
            .map(item -> toViewerView(item, userIds))
            .toList();
    int total = cfpSubmissionService.countMine(eventId, userIds);
    int ownedTotal = cfpSubmissionService.countMineOwned(eventId, userIds);
    boolean hasMore = offset + items.size() < total;
    Integer nextOffset = hasMore ? offset + items.size() : null;
    return Response.ok(new SubmissionListResponse(limit, offset, total, ownedTotal, hasMore, nextOffset, items)).build();
  }
  @GET
  @Path("/config")
  public Response submissionConfig(@PathParam("eventId") String eventId) {
    try {
      return Response.ok(toSubmissionLimitConfigResponse(eventId, AdminUtils.isAdmin(identity))).build();
    } catch (CfpEventConfigService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @GET
  @Path("/event-config")
  @Authenticated
  public Response eventSubmissionConfig(@PathParam("eventId") String eventId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    try {
      CfpEventConfigService.ResolvedEventConfig resolved = cfpEventConfigService.resolveForEvent(eventId);
      Optional<CfpEventConfig> override = cfpEventConfigService.findOverride(eventId);
      return Response.ok(
              new EventSubmissionConfigResponse(
                  resolved.eventId(),
                  resolved.hasOverride(),
                  override.map(this::toEventConfigView).orElse(null),
                  toEventConfigView(resolved)))
          .build();
    } catch (CfpEventConfigService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @PUT
  @Path("/event-config")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateEventSubmissionConfig(
      @PathParam("eventId") String eventId, EventSubmissionConfigUpdateRequest request) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    if (request == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_config")).build();
    }
    try {
      CfpEventConfig override =
          cfpEventConfigService.upsert(
              eventId,
              new CfpEventConfigService.UpdateRequest(
                  request.acceptingSubmissions(),
                  request.opensAt(),
                  request.closesAt(),
                  request.maxPerUser(),
                  request.testingModeEnabled()));
      CfpEventConfigService.ResolvedEventConfig resolved = cfpEventConfigService.resolveForEvent(eventId);
      return Response.ok(
              new EventSubmissionConfigResponse(
                  resolved.eventId(),
                  true,
                  toEventConfigView(override),
                  toEventConfigView(resolved)))
          .build();
    } catch (CfpEventConfigService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @DELETE
  @Path("/event-config")
  @Authenticated
  public Response clearEventSubmissionConfig(@PathParam("eventId") String eventId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    try {
      boolean cleared = cfpEventConfigService.clearOverride(eventId);
      CfpEventConfigService.ResolvedEventConfig resolved = cfpEventConfigService.resolveForEvent(eventId);
      return Response.ok(
              new EventSubmissionConfigClearResponse(
                  resolved.eventId(),
                  cleared,
                  toEventConfigView(resolved)))
          .build();
    } catch (CfpEventConfigService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @POST
  @Path("/publish-results")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response publishResults(
      @PathParam("eventId") String eventId, PublishResultsRequest request) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    try {
      CfpEventConfig config =
          cfpEventConfigService.publishResults(
              eventId,
              currentUserId().orElse("admin"),
              request != null ? request.acceptedMessage() : null,
              request != null ? request.rejectedMessage() : null);
      List<CfpSubmission> submissions =
          cfpSubmissionService.listByEventAll(eventId, Optional.empty(), CfpSubmissionService.SortOrder.UPDATED_DESC);
      int acceptedPublished = 0;
      int rejectedPublished = 0;
      for (CfpSubmission submission : submissions) {
        CfpSubmissionStatus visibleStatus = cfpSubmissionService.visibleStatus(submission);
        if (visibleStatus == CfpSubmissionStatus.ACCEPTED) {
          applyAcceptedPublicationSideEffects(submission);
          acceptedPublished++;
        } else if (visibleStatus == CfpSubmissionStatus.REJECTED) {
          rejectedPublished++;
        }
      }
      metrics.recordFunnelStep("cfp.results.publish");
      return Response.ok(
              new PublishResultsResponse(
                  eventId,
                  acceptedPublished,
                  rejectedPublished,
                  config.resultsPublishedAt(),
                  config.resultsPublishedBy(),
                  config.acceptedResultsMessage(),
                  config.rejectedResultsMessage()))
          .build();
    } catch (CfpEventConfigService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    }
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
    cfpConfigService.update(requestedLimit, requestedTesting);
    try {
      return Response.ok(toSubmissionLimitConfigResponse(eventId, true)).build();
    } catch (CfpEventConfigService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    }
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
    if (!admin && !containsUserId(userIds, existing.get().proposerUserId())) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "owner_required")).build();
    }
    try {
      CfpSubmission deleted = cfpSubmissionService.delete(eventId, id);
      return Response.ok(new SubmissionResponse(admin ? toAdminView(deleted) : toViewerView(deleted, userIds))).build();
    } catch (CfpSubmissionService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    } catch (IllegalStateException e) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "cfp_storage_unavailable", "detail", storageDetail(e)))
          .build();
    }
  }

  @GET
  @Path("/stats")
  @Authenticated
  public Response stats(@PathParam("eventId") String eventId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    CfpSubmissionService.EventStats stats = cfpSubmissionService.statsByEvent(eventId);
    return Response.ok(
            new SubmissionStatsResponse(
                stats.total(),
                stats.countsByStatus().getOrDefault(CfpSubmissionStatus.PENDING, 0),
                stats.countsByStatus().getOrDefault(CfpSubmissionStatus.UNDER_REVIEW, 0),
                stats.countsByStatus().getOrDefault(CfpSubmissionStatus.ACCEPTED, 0),
                stats.countsByStatus().getOrDefault(CfpSubmissionStatus.REJECTED, 0),
                stats.countsByStatus().getOrDefault(CfpSubmissionStatus.WITHDRAWN, 0),
                stats.latestUpdatedAt()))
        .build();
  }

  @GET
  @Authenticated
  public Response listForModeration(
      @PathParam("eventId") String eventId,
      @QueryParam("status") String status,
      @QueryParam("sort") String sort,
      @QueryParam("proposed_by") String proposedBy,
      @QueryParam("title") String title,
      @QueryParam("track") String track,
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
    CfpSubmissionService.ModerationFilter moderationFilter =
        new CfpSubmissionService.ModerationFilter(proposedBy, title, track);
    List<SubmissionView> items =
        cfpSubmissionService
            .listByEvent(eventId, statusFilter, moderationFilter, sortOrder, limit, offset)
            .stream()
            .map(item -> safeRefreshPanelists(eventId, item))
            .map(this::toAdminView)
            .toList();
    int total = cfpSubmissionService.countByEvent(eventId, statusFilter, moderationFilter);
    boolean hasMore = offset + items.size() < total;
    Integer nextOffset = hasMore ? offset + items.size() : null;
    return Response.ok(new SubmissionListResponse(limit, offset, total, total, hasMore, nextOffset, items)).build();
  }

  @GET
  @Path("/{id}")
  @Authenticated
  public Response getSubmissionDetail(
      @PathParam("eventId") String eventId, @PathParam("id") String id) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    Optional<CfpSubmission> existing = cfpSubmissionService.findById(id);
    if (existing.isEmpty() || !eventId.equals(existing.get().eventId())) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "submission_not_found")).build();
    }
    CfpSubmission refreshed = safeRefreshPanelists(eventId, existing.get());
    return Response.ok(new SubmissionResponse(toAdminView(refreshed))).build();
  }

  @GET
  @Path("/export.csv")
  @Authenticated
  @Produces("text/csv")
  public Response exportModerationCsv(
      @PathParam("eventId") String eventId,
      @QueryParam("status") String status,
      @QueryParam("sort") String sort,
      @QueryParam("proposed_by") String proposedBy,
      @QueryParam("title") String title,
      @QueryParam("track") String track) {
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

    CfpSubmissionService.ModerationFilter moderationFilter =
        new CfpSubmissionService.ModerationFilter(proposedBy, title, track);
    List<CfpSubmission> items =
        cfpSubmissionService.listByEventAll(eventId, statusFilter, moderationFilter, sortOrder);
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
              id,
              status.get(),
              currentUserId().orElse("admin"),
              request != null ? request.note() : null,
              request != null ? request.expectedUpdatedAt() : null);
      metrics.recordFunnelStep("cfp.submission.status");
      metrics.recordFunnelStep("cfp.submission.status." + status.get().apiValue());
      if (status.get() == CfpSubmissionStatus.ACCEPTED && cfpSubmissionService.areResultsPublished(eventId)) {
        updated = safeRefreshPanelists(eventId, updated);
        applyAcceptedPublicationSideEffects(updated);
      }
      cfpInsightsService.recordStatusChange(existing.get(), updated);
      return Response.ok(new SubmissionResponse(toAdminView(updated))).build();
    } catch (CfpSubmissionService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    } catch (CfpSubmissionService.InvalidTransitionException e) {
      return Response.status(Response.Status.CONFLICT).entity(Map.of("error", e.getMessage())).build();
    } catch (CfpSubmissionService.ValidationException e) {
      Response.Status statusCode =
          "stale_submission".equals(e.getMessage()) ? Response.Status.CONFLICT : Response.Status.BAD_REQUEST;
      return Response.status(statusCode).entity(Map.of("error", e.getMessage())).build();
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
              currentUserId().orElse("admin"),
              request.expectedUpdatedAt());
      cfpInsightsService.recordRatingUpdated(updated);
      return Response.ok(new SubmissionResponse(toAdminView(updated))).build();
    } catch (CfpSubmissionService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    } catch (CfpSubmissionService.ValidationException e) {
      Response.Status statusCode =
          "stale_submission".equals(e.getMessage()) ? Response.Status.CONFLICT : Response.Status.BAD_REQUEST;
      return Response.status(statusCode).entity(Map.of("error", e.getMessage())).build();
    } catch (IllegalStateException e) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "cfp_storage_unavailable", "detail", storageDetail(e)))
          .build();
    }
  }

  @PUT
  @Path("/{id}/panelists")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updatePanelists(
      @PathParam("eventId") String eventId,
      @PathParam("id") String id,
      UpdatePanelistsRequest request) {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    Optional<CfpSubmission> existing = cfpSubmissionService.findById(id);
    if (existing.isEmpty() || !eventId.equals(existing.get().eventId())) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "submission_not_found")).build();
    }
    boolean admin = AdminUtils.isAdmin(identity);
    if (!admin && !containsUserId(userIds, existing.get().proposerUserId())) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "owner_required")).build();
    }
    try {
      List<CfpSubmissionService.PanelistInput> panelists =
          request == null || request.panelists() == null
              ? List.of()
              : request.panelists().stream()
                  .map(item -> new CfpSubmissionService.PanelistInput(item.name(), item.email(), item.userId()))
                  .toList();
      CfpSubmission updated =
          cfpSubmissionService.updatePanelists(
              eventId,
              id,
              panelists,
              currentUserId().orElse(existing.get().proposerUserId()),
              request != null ? request.expectedUpdatedAt() : null);
      if (cfpSubmissionService.visibleStatus(updated) == CfpSubmissionStatus.ACCEPTED && updated.panelists() != null) {
        for (CfpPanelist panelist : updated.panelists()) {
          if (panelist == null || panelist.userId() == null || panelist.userId().isBlank()) {
            continue;
          }
          eventOperationsService.upsertStaff(
              updated.eventId(),
              panelist.userId(),
              panelist.name(),
              EventStaffRole.SPEAKER,
              "cfp_panelist",
              true);
        }
      }
      metrics.recordFunnelStep("cfp.submission.panelists.update");
      return Response.ok(new SubmissionResponse(toViewerView(updated, userIds))).build();
    } catch (CfpSubmissionService.ValidationException e) {
      Response.Status statusCode =
          "stale_submission".equals(e.getMessage()) ? Response.Status.CONFLICT : Response.Status.BAD_REQUEST;
      return Response.status(statusCode).entity(Map.of("error", e.getMessage())).build();
    } catch (CfpSubmissionService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @POST
  @Path("/{id}/presentation")
  @Authenticated
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadPresentation(
      @PathParam("eventId") String eventId,
      @PathParam("id") String id,
      @FormParam("file") FileUpload file,
      @FormParam("expectedUpdatedAt") String expectedUpdatedAtRaw) {
    Set<String> userIds = currentUserIds();
    if (userIds.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "user_not_authenticated")).build();
    }
    Optional<CfpSubmission> existing = cfpSubmissionService.findById(id);
    if (existing.isEmpty() || !eventId.equals(existing.get().eventId())) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "submission_not_found")).build();
    }
    boolean admin = AdminUtils.isAdmin(identity);
    boolean owner = containsUserId(userIds, existing.get().proposerUserId());
    boolean panelist =
        existing.get().panelists() != null
            && existing.get().panelists().stream()
                .anyMatch(item -> item != null && containsUserId(userIds, item.userId()));
    if (!admin && !owner && !panelist) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "owner_required")).build();
    }
    if (file == null || file.fileName() == null || file.fileName().isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "presentation_file_required")).build();
    }
    try {
      StoredPresentation stored = storePresentationPdf(eventId, id, file);
      Instant expectedUpdatedAt = parseInstant(expectedUpdatedAtRaw);
      CfpPresentationAsset asset =
          new CfpPresentationAsset(
              stored.fileName(),
              stored.contentType(),
              stored.sizeBytes(),
              stored.storagePath(),
              currentUserId().orElse(existing.get().proposerUserId()),
              Instant.now());
      CfpSubmission updated =
          cfpSubmissionService.updatePresentationAsset(
              eventId,
              id,
              asset,
              currentUserId().orElse(existing.get().proposerUserId()),
              expectedUpdatedAt);
      metrics.recordFunnelStep("cfp.submission.presentation.upload");
      return Response.ok(new SubmissionResponse(toViewerView(updated, userIds))).build();
    } catch (CfpSubmissionService.ValidationException e) {
      Response.Status statusCode =
          "stale_submission".equals(e.getMessage()) ? Response.Status.CONFLICT : Response.Status.BAD_REQUEST;
      return Response.status(statusCode).entity(Map.of("error", e.getMessage())).build();
    } catch (CfpSubmissionService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    } catch (IOException e) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(Map.of("error", "presentation_storage_unavailable"))
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
    if (cfpSubmissionService.visibleStatus(submission) != CfpSubmissionStatus.ACCEPTED) {
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
    cfpInsightsService.recordPromoted(submission, speakerId, talkId);

    return Response.ok(new PromoteResponse(speakerId, talkId, createdSpeaker, createdTalk)).build();
  }

  private EventSubmissionConfigView toEventConfigView(CfpEventConfig config) {
    if (config == null) {
      return null;
    }
    return new EventSubmissionConfigView(
        config.acceptingSubmissions(),
        config.opensAt(),
        config.closesAt(),
        config.maxSubmissionsPerUserPerEvent(),
        config.testingModeEnabled(),
        null,
        config.resultsPublishedAt() != null,
        config.resultsPublishedAt(),
        config.resultsPublishedBy(),
        config.acceptedResultsMessage(),
        config.rejectedResultsMessage());
  }

  private static EventSubmissionConfigView toEventConfigView(CfpEventConfigService.ResolvedEventConfig config) {
    return new EventSubmissionConfigView(
        config.acceptingSubmissions(),
        config.opensAt(),
        config.closesAt(),
        config.maxSubmissionsPerUserPerEvent(),
        config.testingModeEnabled(),
        config.currentlyOpen(),
        config.resultsPublished(),
        config.resultsPublishedAt(),
        config.resultsPublishedBy(),
        config.acceptedResultsMessage(),
        config.rejectedResultsMessage());
  }

  private SubmissionLimitConfigResponse toSubmissionLimitConfigResponse(String eventId, boolean admin) {
    CfpEventConfigService.ResolvedEventConfig resolved = cfpEventConfigService.resolveForEvent(eventId);
    return new SubmissionLimitConfigResponse(
        resolved.maxSubmissionsPerUserPerEvent(),
        CfpSubmissionService.MIN_SUBMISSIONS_PER_USER_PER_EVENT,
        CfpSubmissionService.MAX_SUBMISSIONS_PER_USER_PER_EVENT,
        admin,
        resolved.testingModeEnabled(),
        resolved.acceptingSubmissions(),
        resolved.opensAt(),
        resolved.closesAt(),
        resolved.currentlyOpen(),
        resolved.hasOverride());
  }

  private SubmissionView toAdminView(CfpSubmission submission) {
    return toView(submission, Set.of(), true);
  }

  private SubmissionView toViewerView(CfpSubmission submission, Set<String> viewerUserIds) {
    return toView(submission, viewerUserIds, false);
  }

  private SubmissionView toView(CfpSubmission submission, Set<String> viewerUserIds, boolean adminView) {
    List<PanelistView> panelists =
        submission.panelists() == null
            ? List.of()
            : submission.panelists().stream().map(this::toPanelistView).toList();
    int pendingPanelists =
        (int)
            panelists.stream()
                .filter(item -> CfpPanelistStatus.PENDING_LOGIN.apiValue().equals(item.status()))
                .count();
    PresentationAssetView presentationAsset = toPresentationAssetView(submission.presentationAsset());
    String viewerRole = resolveViewerRole(submission, viewerUserIds);
    boolean canEdit = "owner".equals(viewerRole);
    CfpSubmissionStatus internalStatus =
        submission.status() != null ? submission.status() : CfpSubmissionStatus.PENDING;
    CfpSubmissionStatus publicStatus = cfpSubmissionService.visibleStatus(submission);
    String resultMessage = cfpSubmissionService.resultMessage(submission);
    Instant resultsPublishedAt = cfpSubmissionService.resultsPublishedAt(submission.eventId());
    boolean resultsPublished = resultsPublishedAt != null;
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
        (adminView ? internalStatus : publicStatus).apiValue(),
        internalStatus.apiValue(),
        publicStatus.apiValue(),
        submission.createdAt(),
        submission.updatedAt(),
        submission.moderatedAt(),
        submission.moderatedBy(),
        submission.moderationNote(),
        submission.ratingTechnicalDetail(),
        submission.ratingNarrative(),
        submission.ratingContentImpact(),
        CfpSubmissionService.calculateWeightedScore(submission),
        panelists,
        pendingPanelists,
        presentationAsset,
        resultsPublished,
        resultsPublishedAt,
        resultMessage,
        viewerRole,
        canEdit);
  }

  private static String resolveViewerRole(CfpSubmission submission, Set<String> viewerUserIds) {
    if (submission == null || viewerUserIds == null || viewerUserIds.isEmpty()) {
      return "viewer";
    }
    String proposer = normalizeUserId(submission.proposerUserId());
    if (proposer != null && viewerUserIds.contains(proposer)) {
      return "owner";
    }
    if (submission.panelists() != null) {
      for (CfpPanelist panelist : submission.panelists()) {
        if (panelist == null) {
          continue;
        }
        String panelistUserId = normalizeUserId(panelist.userId());
        if (panelistUserId != null && viewerUserIds.contains(panelistUserId)) {
          return "panelist";
        }
      }
    }
    return "viewer";
  }

  private CfpSubmission safeRefreshPanelists(String eventId, CfpSubmission submission) {
    if (submission == null) {
      return null;
    }
    if (submission.panelists() == null || submission.panelists().isEmpty()) {
      return submission;
    }
    try {
      CfpSubmission refreshed = cfpSubmissionService.refreshPanelistsLinkState(eventId, submission.id());
      if (cfpSubmissionService.visibleStatus(refreshed) == CfpSubmissionStatus.ACCEPTED
          && refreshed.panelists() != null) {
        for (CfpPanelist panelist : refreshed.panelists()) {
          if (panelist == null || panelist.userId() == null || panelist.userId().isBlank()) {
            continue;
          }
          eventOperationsService.upsertStaff(
              refreshed.eventId(),
              panelist.userId(),
              panelist.name(),
              EventStaffRole.SPEAKER,
              "cfp_panelist",
              true);
        }
      }
      return refreshed;
    } catch (Exception ignored) {
      return submission;
    }
  }

  private PanelistView toPanelistView(CfpPanelist panelist) {
    if (panelist == null) {
      return new PanelistView("", "", null, null, CfpPanelistStatus.PENDING_LOGIN.apiValue(), null, null);
    }
    return new PanelistView(
        panelist.id(),
        panelist.name(),
        panelist.email(),
        panelist.userId(),
        panelist.status(),
        panelist.createdAt(),
        panelist.updatedAt());
  }

  private PresentationAssetView toPresentationAssetView(CfpPresentationAsset asset) {
    if (asset == null) {
      return null;
    }
    return new PresentationAssetView(
        asset.fileName(),
        asset.contentType(),
        asset.sizeBytes(),
        asset.uploadedByUserId(),
        asset.uploadedAt());
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

  private static boolean containsUserId(Set<String> userIds, String candidate) {
    if (userIds == null || userIds.isEmpty()) {
      return false;
    }
    String normalized = normalizeUserId(candidate);
    return normalized != null && userIds.contains(normalized);
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
      return Optional.of(CfpSubmissionStatus.UNDER_REVIEW);
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
    if ("updated".equalsIgnoreCase(sort) || "latest_update".equalsIgnoreCase(sort)) {
      return CfpSubmissionService.SortOrder.UPDATED_DESC;
    }
    if ("score".equalsIgnoreCase(sort) || "weighted".equalsIgnoreCase(sort)) {
      return CfpSubmissionService.SortOrder.SCORE_DESC;
    }
    return null;
  }

  private String buildCsv(List<CfpSubmission> items) {
    StringBuilder csv = new StringBuilder();
    csv.append(
            "id,event_id,title,status,public_status,proposer_user_id,proposer_name,created_at,updated_at,moderated_at,moderated_by,"
                + "level,format,duration_min,language,track,"
                + "rating_technical_detail,rating_narrative,rating_content_impact,rating_weighted,"
                + "panelists_count,panelists_pending,presentation_file,moderation_note,links")
        .append('\n');

    for (CfpSubmission item : items) {
      Double weighted = CfpSubmissionService.calculateWeightedScore(item);
      int panelistsCount = item.panelists() == null ? 0 : item.panelists().size();
      int panelistsPending =
          item.panelists() == null
              ? 0
              : (int)
                  item.panelists().stream()
                      .filter(p -> p != null && CfpPanelistStatus.PENDING_LOGIN.apiValue().equals(p.status()))
                      .count();
      csv.append(csvValue(item.id())).append(',')
          .append(csvValue(item.eventId())).append(',')
          .append(csvValue(item.title())).append(',')
          .append(csvValue(item.status() != null ? item.status().apiValue() : null)).append(',')
          .append(csvValue(cfpSubmissionService.visibleStatus(item).apiValue())).append(',')
          .append(csvValue(item.proposerUserId())).append(',')
          .append(csvValue(item.proposerName())).append(',')
          .append(csvValue(item.createdAt() != null ? item.createdAt().toString() : null)).append(',')
          .append(csvValue(item.updatedAt() != null ? item.updatedAt().toString() : null)).append(',')
          .append(csvValue(item.moderatedAt() != null ? item.moderatedAt().toString() : null)).append(',')
          .append(csvValue(item.moderatedBy())).append(',')
          .append(csvValue(item.level())).append(',')
          .append(csvValue(item.format())).append(',')
          .append(csvValue(item.durationMin())).append(',')
          .append(csvValue(item.language())).append(',')
          .append(csvValue(item.track())).append(',')
          .append(csvValue(item.ratingTechnicalDetail())).append(',')
          .append(csvValue(item.ratingNarrative())).append(',')
          .append(csvValue(item.ratingContentImpact())).append(',')
          .append(csvValue(weighted)).append(',')
          .append(csvValue(panelistsCount)).append(',')
          .append(csvValue(panelistsPending)).append(',')
          .append(csvValue(item.presentationAsset() != null ? item.presentationAsset().fileName() : null)).append(',')
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

  private void applyAcceptedPublicationSideEffects(CfpSubmission submission) {
    if (submission == null || submission.proposerUserId() == null || submission.proposerUserId().isBlank()) {
      return;
    }
    if (!alreadyAwardedAccepted(submission.proposerUserId(), submission.id())) {
      gamificationService.award(submission.proposerUserId(), GamificationActivity.CFP_ACCEPTED, submission.id());
      metrics.recordFunnelStep("cfp_approved");
    }
    userProfileService.activateSpeakerProfile(
        submission.proposerUserId(),
        submission.proposerName() != null ? submission.proposerName() : submission.proposerUserId(),
        submission.proposerUserId());
    eventOperationsService.upsertStaff(
        submission.eventId(),
        submission.proposerUserId(),
        submission.proposerName(),
        EventStaffRole.SPEAKER,
        "cfp_acceptance",
        true);
    if (submission.panelists() == null) {
      return;
    }
    for (CfpPanelist panelist : submission.panelists()) {
      if (panelist == null || panelist.userId() == null || panelist.userId().isBlank()) {
        continue;
      }
      eventOperationsService.upsertStaff(
          submission.eventId(),
          panelist.userId(),
          panelist.name(),
          EventStaffRole.SPEAKER,
          "cfp_panelist",
          true);
    }
  }

  private boolean alreadyAwardedAccepted(String userId, String submissionId) {
    if (userId == null || userId.isBlank() || submissionId == null || submissionId.isBlank()) {
      return false;
    }
    return userProfileService
        .find(userId)
        .map(profile -> profile.hasHistoryTitle(GamificationActivity.CFP_ACCEPTED.title() + " · " + submissionId))
        .orElse(false);
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
    int effectiveMax = Math.max(4, maxLength);
    String input = raw.trim().toLowerCase(Locale.ROOT);
    if (input.isBlank()) {
      return "item";
    }
    StringBuilder out = new StringBuilder(Math.min(input.length(), effectiveMax));
    boolean lastDash = false;
    for (int i = 0; i < input.length(); i++) {
      char ch = input.charAt(i);
      boolean alphaNum = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
      if (alphaNum) {
        if (out.length() >= effectiveMax) {
          break;
        }
        out.append(ch);
        lastDash = false;
        continue;
      }
      if (!lastDash && out.length() > 0 && out.length() < effectiveMax) {
        out.append('-');
        lastDash = true;
      }
    }
    while (out.length() > 0 && out.charAt(out.length() - 1) == '-') {
      out.deleteCharAt(out.length() - 1);
    }
    return out.length() == 0 ? "item" : out.toString();
  }

  private static String sanitizeDisplayText(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim().replaceAll("\\s+", " ");
    return value.isBlank() ? null : value;
  }

  private static String normalizeUserId(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim().toLowerCase(Locale.ROOT);
    return value.isBlank() ? null : value;
  }

  private StoredPresentation storePresentationPdf(String eventId, String submissionId, FileUpload file)
      throws IOException {
    String safeEventId = sanitizeIdToken(eventId, 40);
    String safeSubmissionId = sanitizeIdToken(submissionId, 48);
    java.nio.file.Path uploadsRoot = resolveDataDir().resolve("uploads").resolve("cfp").normalize();
    java.nio.file.Path baseDir =
        uploadsRoot.resolve(safeEventId).resolve(safeSubmissionId).normalize();
    if (!baseDir.startsWith(uploadsRoot)) {
      throw new IOException("invalid_presentation_path");
    }
    Files.createDirectories(baseDir);
    String originalName = file.fileName() != null ? file.fileName().trim() : "presentation.pdf";
    String sanitizedName = sanitizeFileName(originalName);
    if (!sanitizedName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
      sanitizedName = sanitizedName + ".pdf";
    }
    java.nio.file.Path target = baseDir.resolve("presentation.pdf");
    java.nio.file.Path source = file.uploadedFile();
    if (source == null || !Files.exists(source)) {
      throw new IOException("uploaded_file_missing");
    }
    long sizeBytes = Files.size(source);
    if (sizeBytes <= 0 || sizeBytes > 25L * 1024L * 1024L) {
      throw new IOException("invalid_presentation_size");
    }
    String contentType = file.contentType();
    String normalizedType = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "application/pdf";
    if (!normalizedType.contains("pdf")) {
      throw new IOException("invalid_presentation_content_type");
    }
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    return new StoredPresentation(sanitizedName, "application/pdf", sizeBytes, target.toString());
  }

  private java.nio.file.Path resolveDataDir() {
    String sysProp = System.getProperty("homedir.data.dir");
    String raw = (sysProp != null && !sysProp.isBlank()) ? sysProp : dataDirPath;
    return Paths.get(raw == null || raw.isBlank() ? "data" : raw);
  }

  private static String sanitizeFileName(String raw) {
    if (raw == null || raw.isBlank()) {
      return "presentation.pdf";
    }
    StringBuilder out = new StringBuilder(raw.length());
    boolean lastDash = false;
    for (int i = 0; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      boolean safe =
          (ch >= 'a' && ch <= 'z')
              || (ch >= 'A' && ch <= 'Z')
              || (ch >= '0' && ch <= '9')
              || ch == '.'
              || ch == '_'
              || ch == '-';
      if (ch == '/' || ch == '\\') {
        safe = false;
      }
      if (safe) {
        out.append(ch);
        lastDash = false;
      } else if (!lastDash) {
        out.append('-');
        lastDash = true;
      }
    }
    int start = 0;
    int end = out.length();
    while (start < end && out.charAt(start) == '-') {
      start++;
    }
    while (end > start && out.charAt(end - 1) == '-') {
      end--;
    }
    String value = start >= end ? "" : out.substring(start, end);
    if (value.isBlank()) {
      return "presentation.pdf";
    }
    if (value.length() > 120) {
      value = value.substring(value.length() - 120);
    }
    return value;
  }

  private static Instant parseInstant(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw.trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private record StoredPresentation(String fileName, String contentType, long sizeBytes, String storagePath) {}

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

  public record UpdateStatusRequest(
      String status,
      String note,
      @JsonProperty("expected_updated_at") Instant expectedUpdatedAt) {}

  public record PublishResultsRequest(
      @JsonProperty("accepted_message") String acceptedMessage,
      @JsonProperty("rejected_message") String rejectedMessage) {}

  public record UpdateRatingRequest(
      @JsonProperty("technical_detail") Integer technicalDetail,
      Integer narrative,
      @JsonProperty("content_impact") Integer contentImpact,
      @JsonProperty("expected_updated_at") Instant expectedUpdatedAt) {}

  public record UpdatePanelistsRequest(
      List<PanelistInputRequest> panelists,
      @JsonProperty("expected_updated_at") Instant expectedUpdatedAt) {}

  public record PanelistInputRequest(
      String name,
      String email,
      @JsonProperty("user_id") String userId) {}

  public record PromoteResponse(
      @JsonProperty("speaker_id") String speakerId,
      @JsonProperty("talk_id") String talkId,
      @JsonProperty("created_speaker") boolean createdSpeaker,
      @JsonProperty("created_talk") boolean createdTalk) {}

  public record SubmissionResponse(SubmissionView item) {}

  public record SubmissionListResponse(
      int limit,
      int offset,
      int total,
      @JsonProperty("owned_total") int ownedTotal,
      @JsonProperty("has_more") boolean hasMore,
      @JsonProperty("next_offset") Integer nextOffset,
      List<SubmissionView> items) {}

  public record SubmissionStatsResponse(
      int total,
      int pending,
      @JsonProperty("under_review") int underReview,
      int accepted,
      int rejected,
      int withdrawn,
      @JsonProperty("latest_updated_at") Instant latestUpdatedAt) {}
  public record SubmissionLimitConfigUpdateRequest(
      @JsonProperty("max_per_user") Integer maxPerUser,
      @JsonProperty("testing_mode_enabled") Boolean testingModeEnabled) {}

  public record EventSubmissionConfigUpdateRequest(
      @JsonProperty("accepting_submissions") Boolean acceptingSubmissions,
      @JsonProperty("opens_at") Instant opensAt,
      @JsonProperty("closes_at") Instant closesAt,
      @JsonProperty("max_per_user") Integer maxPerUser,
      @JsonProperty("testing_mode_enabled") Boolean testingModeEnabled) {}

  public record EventSubmissionConfigView(
      @JsonProperty("accepting_submissions") boolean acceptingSubmissions,
      @JsonProperty("opens_at") Instant opensAt,
      @JsonProperty("closes_at") Instant closesAt,
      @JsonProperty("max_per_user") Integer maxPerUser,
      @JsonProperty("testing_mode_enabled") Boolean testingModeEnabled,
      @JsonProperty("currently_open") Boolean currentlyOpen,
      @JsonProperty("results_published") boolean resultsPublished,
      @JsonProperty("results_published_at") Instant resultsPublishedAt,
      @JsonProperty("results_published_by") String resultsPublishedBy,
      @JsonProperty("accepted_results_message") String acceptedResultsMessage,
      @JsonProperty("rejected_results_message") String rejectedResultsMessage) {}

  public record EventSubmissionConfigResponse(
      @JsonProperty("event_id") String eventId,
      @JsonProperty("has_override") boolean hasOverride,
      EventSubmissionConfigView override,
      EventSubmissionConfigView effective) {}

  public record EventSubmissionConfigClearResponse(
      @JsonProperty("event_id") String eventId,
      boolean cleared,
      EventSubmissionConfigView effective) {}

  public record PublishResultsResponse(
      @JsonProperty("event_id") String eventId,
      @JsonProperty("accepted_published") int acceptedPublished,
      @JsonProperty("rejected_published") int rejectedPublished,
      @JsonProperty("results_published_at") Instant resultsPublishedAt,
      @JsonProperty("results_published_by") String resultsPublishedBy,
      @JsonProperty("accepted_message") String acceptedMessage,
      @JsonProperty("rejected_message") String rejectedMessage) {}

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
      @JsonProperty("testing_mode_enabled") boolean testingModeEnabled,
      @JsonProperty("accepting_submissions") boolean acceptingSubmissions,
      @JsonProperty("opens_at") Instant opensAt,
      @JsonProperty("closes_at") Instant closesAt,
      @JsonProperty("currently_open") boolean currentlyOpen,
      @JsonProperty("has_event_override") boolean hasEventOverride) {}

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
      @JsonProperty("internal_status") String internalStatus,
      @JsonProperty("public_status") String publicStatus,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("updated_at") Instant updatedAt,
      @JsonProperty("moderated_at") Instant moderatedAt,
      @JsonProperty("moderated_by") String moderatedBy,
      @JsonProperty("moderation_note") String moderationNote,
      @JsonProperty("rating_technical_detail") Integer ratingTechnicalDetail,
      @JsonProperty("rating_narrative") Integer ratingNarrative,
      @JsonProperty("rating_content_impact") Integer ratingContentImpact,
      @JsonProperty("rating_weighted") Double ratingWeighted,
      List<PanelistView> panelists,
      @JsonProperty("pending_panelists") int pendingPanelists,
      @JsonProperty("presentation_asset") PresentationAssetView presentationAsset,
      @JsonProperty("results_published") boolean resultsPublished,
      @JsonProperty("results_published_at") Instant resultsPublishedAt,
      @JsonProperty("result_message") String resultMessage,
      @JsonProperty("viewer_role") String viewerRole,
      @JsonProperty("can_edit") boolean canEdit) {}

  public record PanelistView(
      String id,
      String name,
      String email,
      @JsonProperty("user_id") String userId,
      String status,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("updated_at") Instant updatedAt) {}

  public record PresentationAssetView(
      @JsonProperty("file_name") String fileName,
      @JsonProperty("content_type") String contentType,
      @JsonProperty("size_bytes") long sizeBytes,
      @JsonProperty("uploaded_by_user_id") String uploadedByUserId,
      @JsonProperty("uploaded_at") Instant uploadedAt) {}
}
