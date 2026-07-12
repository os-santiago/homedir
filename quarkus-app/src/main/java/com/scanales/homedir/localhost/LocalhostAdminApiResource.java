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

  @Inject EventService eventService;
  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject VolunteerApplicationService volunteerApplicationService;
  @Inject UsageMetricsService metricsService;
  @Inject UserProfileService userProfileService;

  @ConfigProperty(name = "LOCALHOST_ADMIN_TOKEN")
  Optional<String> adminToken;

  /** Validates that: 1. Request comes from localhost 2. Bearer token matches configured token */
  private Response validateAccess(HttpServerRequest request, String authHeader) {
    // Check 1: Request must come from localhost
    String remoteHost = request.remoteAddress().host();
    if (!isLocalhost(remoteHost)) {
      LOG.warnf("Rejected localhost-admin request from non-localhost address: %s", remoteHost);
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
      LOG.warnf("Invalid localhost admin token attempt from %s", remoteHost);
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

      LOG.infof("CFP %s updated to status %s via localhost admin API", cfpId, newStatus);

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
      LOG.errorf(e, "Failed to update CFP %s", cfpId);
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

      LOG.infof("Added %d XP to user %s via localhost admin API: %s", amount, userId, reason);

      return Response.ok(updated).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "invalid_quest_class"))
          .build();
    } catch (Exception e) {
      LOG.errorf(e, "Failed to add XP to user %s", userId);
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

      LOG.infof("Updated quest class for user %s to %s via localhost admin API", userId, qc);

      return Response.ok(updated).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "invalid_quest_class"))
          .build();
    } catch (Exception e) {
      LOG.errorf(e, "Failed to update quest class for user %s", userId);
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
}
