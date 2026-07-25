package com.scanales.homedir.localhost;

import com.scanales.homedir.cfp.CfpSubmission;
import com.scanales.homedir.cfp.CfpSubmissionService;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.service.UserProfileService;
import com.scanales.homedir.volunteers.VolunteerApplicationService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Administrative API resource that: 1. Only accepts connections from localhost 2. Requires Bearer
 * token authentication 3. Provides full administrative access to CFPs, users, events, etc.
 *
 * <p>Set environment variable: LOCALHOST_ADMIN_TOKEN=your-secure-token
 */
@Path("/api/localhost-admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LocalhostAdminApiResource {

  private static final Logger LOG = Logger.getLogger(LocalhostAdminApiResource.class);

  private static String sanitizeLog(String value) {
    if (value == null) return "null";
    return value.replaceAll("[\\p{Cntrl}&&[^\\t]]", "");
  }

  @Inject EventService eventService;
  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject VolunteerApplicationService volunteerApplicationService;
  @Inject UsageMetricsService metricsService;
  @Inject UserProfileService userProfileService;
  @Inject com.scanales.homedir.service.SpeakerService speakerService;

  @ConfigProperty(name = "LOCALHOST_ADMIN_TOKEN")
  Optional<String> adminToken;

  /** Validates that: 1. Request comes from localhost 2. Bearer token matches configured token */
  private Response validateAccess(HttpServerRequest request, String authHeader) {
    // Check 1: Request must come from localhost
    String remoteHost = request.remoteAddress().host();
    if (!isLocalhost(remoteHost)) {
      LOG.warnf("Rejected localhost-admin request from non-localhost address: %s", sanitizeLog(remoteHost));
      return Response.status(Response.Status.FORBIDDEN)
          .entity(
              Map.of(
                  "error",
                  "localhost_only",
                  "message",
                  "This endpoint only accepts connections from localhost"))
          .build();
    }

    // Check 2: Token must be configured
    if (adminToken.isEmpty()) {
      LOG.warn("Localhost admin API accessed but LOCALHOST_ADMIN_TOKEN is not configured");
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(
              Map.of("error", "not_configured", "message", "Localhost admin API is not configured"))
          .build();
    }

    // Check 3: Bearer token must match
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(Map.of("error", "missing_token", "message", "Bearer token required"))
          .build();
    }

    String providedToken = authHeader.substring(7).trim();
    if (!MessageDigest.isEqual(
        providedToken.getBytes(StandardCharsets.UTF_8),
        adminToken.get().getBytes(StandardCharsets.UTF_8))) {
      LOG.warnf("Invalid localhost admin token attempt from %s", sanitizeLog(remoteHost));
      return Response.status(Response.Status.FORBIDDEN)
          .entity(Map.of("error", "invalid_token", "message", "Invalid admin token"))
          .build();
    }

    return null; // Access granted
  }

  private boolean isLocalhost(String host) {
    try {
      return InetAddress.getByName(host).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }

  @GET
  @Path("/status")
  public Response status(
      @Context HttpServerRequest request, @HeaderParam("Authorization") String authHeader) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    Map<String, Object> status = new HashMap<>();
    status.put("authenticated", true);
    status.put("mode", "localhost-admin");
    status.put("health", metricsService.getHealth());
    return Response.ok(status).build();
  }

  @GET
  @Path("/events")
  public Response getEvents(
      @Context HttpServerRequest request, @HeaderParam("Authorization") String authHeader) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    return Response.ok(eventService.listEvents()).build();
  }

  @GET
  @Path("/cfp/all")
  public Response getAllCfpSubmissions(
      @Context HttpServerRequest request, @HeaderParam("Authorization") String authHeader) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    List<com.scanales.homedir.model.Event> events = eventService.listEvents();
    List<CfpSubmission> allSubmissions = new ArrayList<>();
    for (com.scanales.homedir.model.Event event : events) {
      allSubmissions.addAll(
          cfpSubmissionService.listByEventAll(
              event.getId(), Optional.empty(), CfpSubmissionService.SortOrder.CREATED_DESC));
    }
    return Response.ok(allSubmissions).build();
  }

  @GET
  @Path("/cfp/{eventId}/{cfpId}")
  public Response getCfpSubmission(
      @Context HttpServerRequest request,
      @HeaderParam("Authorization") String authHeader,
      @PathParam("eventId") String eventId,
      @PathParam("cfpId") String cfpId) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    Optional<CfpSubmission> submission = cfpSubmissionService.findById(cfpId);
    if (submission.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", "not_found"))
          .build();
    }

    if (!submission.get().eventId().equals(eventId)) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", "not_found"))
          .build();
    }

    return Response.ok(Map.of("item", submission.get())).build();
  }

  @PUT
  @Path("/cfp/{eventId}/{cfpId}/status")
  public Response updateCfpStatus(
      @Context HttpServerRequest request,
      @HeaderParam("Authorization") String authHeader,
      @PathParam("eventId") String eventId,
      @PathParam("cfpId") String cfpId,
      Map<String, Object> body) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    // Extract parameters
    String statusStr = (String) body.get("status");
    String note = (String) body.get("note");
    Object versionObj = body.get("version");

    if (statusStr == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "missing_status"))
          .build();
    }

    // Parse status
    com.scanales.homedir.cfp.CfpSubmissionStatus newStatus;
    try {
      newStatus = com.scanales.homedir.cfp.CfpSubmissionStatus.valueOf(statusStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              Map.of(
                  "error",
                  "invalid_status",
                  "message",
                  "Status must be one of: accepted, rejected, under_review, waitlisted"))
          .build();
    }

    // Update the submission
    try {
      CfpSubmission updated =
          cfpSubmissionService.updateStatus(
              cfpId,
              newStatus,
              "localhost-admin",
              note != null ? note : "Updated via localhost admin API");

      LOG.infof("CFP %s updated to status %s via localhost admin API", sanitizeLog(cfpId), newStatus);

      return Response.ok(Map.of("item", updated)).build();
    } catch (CfpSubmissionService.ValidationException e) {
      String msg = e.getMessage();
      if ("stale_submission".equals(msg)) {
        return Response.status(Response.Status.CONFLICT)
            .entity(
                Map.of(
                    "error",
                    "stale_submission",
                    "message",
                    "Submission was modified by another session"))
            .build();
      }
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", msg != null ? msg : "validation_error"))
          .build();
    } catch (Exception e) {
      LOG.errorf(e, "Failed to update CFP %s", sanitizeLog(cfpId));
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(Map.of("error", "update_failed"))
          .build();
    }
  }

  @GET
  @Path("/users")
  public Response getUsers(
      @Context HttpServerRequest request,
      @HeaderParam("Authorization") String authHeader,
      @QueryParam("query") String query) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    List<com.scanales.homedir.model.UserProfile> users =
        new ArrayList<>(userProfileService.allProfiles().values());

    if (query != null && !query.isBlank()) {
      String lowerQuery = query.toLowerCase();
      users =
          users.stream()
              .filter(
                  u ->
                      u.getUserId().toLowerCase().contains(lowerQuery)
                          || (u.getName() != null && u.getName().toLowerCase().contains(lowerQuery))
                          || (u.getEmail() != null
                              && u.getEmail().toLowerCase().contains(lowerQuery)))
              .toList();
    }

    return Response.ok(users).build();
  }

  @GET
  @Path("/users/{userId}")
  public Response getUser(
      @Context HttpServerRequest request,
      @HeaderParam("Authorization") String authHeader,
      @PathParam("userId") String userId) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    Optional<com.scanales.homedir.model.UserProfile> user = userProfileService.find(userId);
    if (user.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", "user_not_found"))
          .build();
    }

    return Response.ok(user.get()).build();
  }

  @POST
  @Path("/users/{userId}/xp")
  public Response addUserXp(
      @Context HttpServerRequest request,
      @HeaderParam("Authorization") String authHeader,
      @PathParam("userId") String userId,
      Map<String, Object> body) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    Object amountObj = body.get("amount");
    String reason = (String) body.get("reason");
    String questClass = (String) body.get("questClass");

    if (amountObj == null || reason == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              Map.of("error", "missing_parameters", "message", "amount and reason are required"))
          .build();
    }

    int amount = ((Number) amountObj).intValue();

    try {
      com.scanales.homedir.model.QuestClass qc =
          questClass != null
              ? com.scanales.homedir.model.QuestClass.valueOf(questClass.toUpperCase())
              : null;
      com.scanales.homedir.model.UserProfile updated =
          userProfileService.addXp(userId, amount, reason, qc);

      LOG.infof("Added %d XP to user %s via localhost admin API: %s", amount, sanitizeLog(userId), sanitizeLog(reason));

      return Response.ok(updated).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "invalid_quest_class"))
          .build();
    } catch (Exception e) {
      LOG.errorf(e, "Failed to add XP to user %s", sanitizeLog(userId));
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(Map.of("error", "update_failed"))
          .build();
    }
  }

  @POST
  @Path("/users/{userId}/quest-class")
  public Response updateUserClass(
      @Context HttpServerRequest request,
      @HeaderParam("Authorization") String authHeader,
      @PathParam("userId") String userId,
      Map<String, Object> body) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    String questClass = (String) body.get("questClass");

    if (questClass == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "missing_quest_class"))
          .build();
    }

    try {
      com.scanales.homedir.model.QuestClass qc =
          com.scanales.homedir.model.QuestClass.valueOf(questClass.toUpperCase());
      com.scanales.homedir.model.UserProfile updated =
          userProfileService.updateQuestClass(userId, qc);

      LOG.infof("Updated quest class for user %s to %s via localhost admin API", sanitizeLog(userId), qc);

      return Response.ok(updated).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "invalid_quest_class"))
          .build();
    } catch (Exception e) {
      LOG.errorf(e, "Failed to update quest class for user %s", sanitizeLog(userId));
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(Map.of("error", "update_failed", "message", e.getMessage()))
          .build();
    }
  }

  @GET
  @Path("/metrics")
  public Response getMetrics(
      @Context HttpServerRequest request, @HeaderParam("Authorization") String authHeader) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    return Response.ok(metricsService.getSummary()).build();
  }

  // ============================================================================
  // Speaker Management Endpoints
  // ============================================================================

  @GET
  @Path("/speakers")
  public Response getSpeakers(
      @Context HttpServerRequest request, @HeaderParam("Authorization") String authHeader) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    return Response.ok(speakerService.listSpeakers()).build();
  }

  @GET
  @Path("/speakers/{speakerId}")
  public Response getSpeaker(
      @Context HttpServerRequest request,
      @HeaderParam("Authorization") String authHeader,
      @PathParam("speakerId") String speakerId) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    com.scanales.homedir.model.Speaker speaker = speakerService.getSpeaker(speakerId);
    if (speaker == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", "speaker_not_found"))
          .build();
    }

    return Response.ok(speaker).build();
  }

  @POST
  @Path("/speakers")
  public Response createSpeaker(
      @Context HttpServerRequest request,
      @HeaderParam("Authorization") String authHeader,
      Map<String, Object> body) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    String id = (String) body.get("id");
    String name = (String) body.get("name");

    if (id == null || id.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "missing_id", "message", "Speaker id is required"))
          .build();
    }

    if (name == null || name.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "missing_name", "message", "Speaker name is required"))
          .build();
    }

    // Check if speaker already exists
    if (speakerService.getSpeaker(id) != null) {
      return Response.status(Response.Status.CONFLICT)
          .entity(
              Map.of(
                  "error",
                  "speaker_exists",
                  "message",
                  "Speaker with id " + id + " already exists"))
          .build();
    }

    com.scanales.homedir.model.Speaker speaker = new com.scanales.homedir.model.Speaker(id, name);
    speaker.setBio((String) body.get("bio"));
    speaker.setPhotoUrl((String) body.get("photoUrl"));
    speaker.setWebsite((String) body.get("website"));
    speaker.setTwitter((String) body.get("twitter"));
    speaker.setLinkedin((String) body.get("linkedin"));
    speaker.setInstagram((String) body.get("instagram"));

    try {
      speakerService.saveSpeaker(speaker);
      LOG.infof("Created speaker %s via localhost admin API", sanitizeLog(id));
      return Response.status(Response.Status.CREATED).entity(speaker).build();
    } catch (Exception e) {
      LOG.errorf(e, "Failed to create speaker %s", sanitizeLog(id));
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(Map.of("error", "create_failed", "message", e.getMessage()))
          .build();
    }
  }

  @PUT
  @Path("/speakers/{speakerId}")
  public Response updateSpeaker(
      @Context HttpServerRequest request,
      @HeaderParam("Authorization") String authHeader,
      @PathParam("speakerId") String speakerId,
      Map<String, Object> body) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    com.scanales.homedir.model.Speaker existing = speakerService.getSpeaker(speakerId);
    if (existing == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", "speaker_not_found"))
          .build();
    }

    // Update fields if provided
    if (body.containsKey("name")) {
      String name = (String) body.get("name");
      if (name == null || name.isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("error", "invalid_name", "message", "Speaker name cannot be blank"))
            .build();
      }
      existing.setName(name);
    }

    if (body.containsKey("bio")) existing.setBio((String) body.get("bio"));
    if (body.containsKey("photoUrl")) existing.setPhotoUrl((String) body.get("photoUrl"));
    if (body.containsKey("website")) existing.setWebsite((String) body.get("website"));
    if (body.containsKey("twitter")) existing.setTwitter((String) body.get("twitter"));
    if (body.containsKey("linkedin")) existing.setLinkedin((String) body.get("linkedin"));
    if (body.containsKey("instagram")) existing.setInstagram((String) body.get("instagram"));

    try {
      speakerService.saveSpeaker(existing);
      LOG.infof("Updated speaker %s via localhost admin API", sanitizeLog(speakerId));
      return Response.ok(existing).build();
    } catch (Exception e) {
      LOG.errorf(e, "Failed to update speaker %s", sanitizeLog(speakerId));
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(Map.of("error", "update_failed", "message", e.getMessage()))
          .build();
    }
  }

  @POST
  @Path("/speakers/bulk")
  public Response createSpeakersBulk(
      @Context HttpServerRequest request,
      @HeaderParam("Authorization") String authHeader,
      Map<String, Object> body) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> speakersData = (List<Map<String, Object>>) body.get("speakers");

    if (speakersData == null || speakersData.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              Map.of("error", "missing_speakers", "message", "speakers array is required and cannot be empty"))
          .build();
    }

    List<com.scanales.homedir.model.Speaker> created = new ArrayList<>();
    List<Map<String, String>> errors = new ArrayList<>();

    for (int i = 0; i < speakersData.size(); i++) {
      Map<String, Object> speakerData = speakersData.get(i);
      String id = null;

      try {
        id = (String) speakerData.get("id");
        String name = (String) speakerData.get("name");

        if (id == null || id.isBlank()) {
          errors.add(
              Map.of(
                  "index", String.valueOf(i), "error", "missing_id", "message", "Speaker id is required"));
          continue;
        }

        if (name == null || name.isBlank()) {
          errors.add(
              Map.of(
                  "index",
                  String.valueOf(i),
                  "error",
                  "missing_name",
                  "message",
                  "Speaker name is required"));
          continue;
        }

        // Skip if already exists
        if (speakerService.getSpeaker(id) != null) {
          errors.add(
              Map.of(
                  "index",
                  String.valueOf(i),
                  "id",
                  id,
                  "error",
                  "speaker_exists",
                  "message",
                  "Speaker already exists"));
          continue;
        }

        com.scanales.homedir.model.Speaker speaker =
            new com.scanales.homedir.model.Speaker(id, name);
        speaker.setBio((String) speakerData.get("bio"));
        speaker.setPhotoUrl((String) speakerData.get("photoUrl"));
        speaker.setWebsite((String) speakerData.get("website"));
        speaker.setTwitter((String) speakerData.get("twitter"));
        speaker.setLinkedin((String) speakerData.get("linkedin"));
        speaker.setInstagram((String) speakerData.get("instagram"));

        speakerService.saveSpeaker(speaker);
        created.add(speaker);
      } catch (Exception e) {
        errors.add(
            Map.of(
                "index",
                String.valueOf(i),
                "id",
                id,
                "error",
                "save_failed",
                "message",
                e.getMessage()));
      }
    }

    LOG.infof("Bulk created %d speakers via localhost admin API", Integer.valueOf(created.size()));

    Map<String, Object> result = new HashMap<>();
    result.put("created", created);
    result.put("createdCount", created.size());
    result.put("errorCount", errors.size());
    if (!errors.isEmpty()) {
      result.put("errors", errors);
    }

    return Response.ok(result).build();
  }

  // ============================================================================
  // Event Agenda Management Endpoints
  // ============================================================================

  @PUT
  @Path("/events/{eventId}/agenda")
  public Response updateEventAgenda(
      @Context HttpServerRequest request,
      @HeaderParam("Authorization") String authHeader,
      @PathParam("eventId") String eventId,
      Map<String, Object> body) {
    Response validationError = validateAccess(request, authHeader);
    if (validationError != null) return validationError;

    com.scanales.homedir.model.Event event = eventService.getEvent(eventId);
    if (event == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", "event_not_found"))
          .build();
    }

    List<Map<String, Object>> agendaData;
    try {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> rawAgenda = (List<Map<String, Object>>) body.get("agenda");
      agendaData = rawAgenda;
    } catch (ClassCastException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "invalid_agenda", "message", "agenda must be an array of objects"))
          .build();
    }

    if (agendaData == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "missing_agenda", "message", "agenda array is required"))
          .build();
    }

    // Validate speaker references before applying
    List<String> missingSpeakers = new ArrayList<>();
    try {
      for (Map<String, Object> talkData : agendaData) {
        @SuppressWarnings("unchecked")
        List<String> speakerIds = (List<String>) talkData.get("speakers");
        if (speakerIds != null) {
          for (String speakerId : speakerIds) {
            if (speakerService.getSpeaker(speakerId) == null) {
              if (!missingSpeakers.contains(speakerId)) {
                missingSpeakers.add(speakerId);
              }
            }
          }
        }
      }
    } catch (ClassCastException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "invalid_agenda", "message", "Invalid agenda structure"))
          .build();
    }

    if (!missingSpeakers.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              Map.of(
                  "error",
                  "unresolved_speakers",
                  "message",
                  "The following speaker IDs do not exist: " + String.join(", ", missingSpeakers),
                  "missingSpeakers",
                  missingSpeakers))
          .build();
    }

    // Parse and create Talk objects
    List<com.scanales.homedir.model.Talk> talks = new ArrayList<>();
    for (Map<String, Object> talkData : agendaData) {
      try {
        com.scanales.homedir.model.Talk talk = parseTalkFromMap(talkData);
        talks.add(talk);
      } catch (Exception e) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(
                Map.of(
                    "error",
                    "invalid_talk",
                    "message",
                    "Failed to parse talk: " + e.getMessage(),
                    "talkId",
                    talkData.get("id")))
            .build();
      }
    }

    // Sort by day and time before saving
    talks.sort(
        java.util.Comparator.comparingInt(com.scanales.homedir.model.Talk::getDay)
            .thenComparing(
                com.scanales.homedir.model.Talk::getStartTime,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));

    try {
      eventService.saveEvent(event);
      // Apply after successful save
      event.getAgenda().clear();
      event.getAgenda().addAll(talks);
      LOG.infof(
          "Updated agenda for event %s with %d talks via localhost admin API",
          sanitizeLog(eventId), Integer.valueOf(talks.size()));
      return Response.ok(Map.of("event", event, "agendaCount", talks.size())).build();
    } catch (Exception e) {
      LOG.errorf(e, "Failed to update agenda for event %s", sanitizeLog(eventId));
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(Map.of("error", "save_failed", "message", e.getMessage()))
          .build();
    }
  }

  private com.scanales.homedir.model.Talk parseTalkFromMap(Map<String, Object> data) {
    com.scanales.homedir.model.Talk talk = new com.scanales.homedir.model.Talk();

    talk.setId((String) data.get("id"));
    talk.setName((String) data.get("name"));
    talk.setDescription((String) data.get("description"));
    talk.setLocation((String) data.get("location"));

    if (data.get("day") != null) {
      talk.setDay(((Number) data.get("day")).intValue());
    }

    // Parse time fields - use setStartTimeStr which accepts String
    String startTimeStr = (String) data.get("startTimeStr");
    if (startTimeStr == null || startTimeStr.isBlank()) {
      startTimeStr = (String) data.get("startTime");
    }
    if (startTimeStr != null && !startTimeStr.isBlank()) {
      talk.setStartTimeStr(startTimeStr);
    }

    if (data.get("durationMinutes") != null) {
      talk.setDurationMinutes(((Number) data.get("durationMinutes")).intValue());
    }

    if (data.get("break") != null) {
      talk.setBreak((Boolean) data.get("break"));
    }

    // Parse speakers
    @SuppressWarnings("unchecked")
    List<String> speakerIds = (List<String>) data.get("speakers");
    if (speakerIds != null && !speakerIds.isEmpty()) {
      List<com.scanales.homedir.model.Speaker> speakers = new ArrayList<>();
      for (String speakerId : speakerIds) {
        com.scanales.homedir.model.Speaker speaker = speakerService.getSpeaker(speakerId);
        if (speaker != null) {
          speakers.add(speaker);
        }
      }
      talk.setSpeakers(speakers);
    }

    return talk;
  }
}
