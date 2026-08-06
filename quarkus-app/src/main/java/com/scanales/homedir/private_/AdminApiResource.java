package com.scanales.homedir.private_;

import com.scanales.homedir.cfp.CfpSubmissionService;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.UsageMetricsService;
import com.scanales.homedir.util.AdminUtils;
import com.scanales.homedir.volunteers.VolunteerApplicationService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

@Path("/api/private/admin")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminApiResource {

  @Inject SecurityIdentity identity;
  @Inject EventService eventService;
  @Inject CfpSubmissionService cfpSubmissionService;
  @Inject VolunteerApplicationService volunteerApplicationService;
  @Inject UsageMetricsService metricsService;
  @Inject com.scanales.homedir.service.UserProfileService userProfileService;

  private boolean canView() {
    return AdminUtils.canViewAdminBackoffice(identity);
  }

  private boolean canManage() {
    return AdminUtils.canManageAdminBackoffice(identity);
  }

  @GET
  @Path("/status")
  public Response status() {
    if (!canView()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    Map<String, Object> status = new HashMap<>();
    status.put("authenticated", true);
    status.put("email", identity.getPrincipal().getName());
    status.put("canView", canView());
    status.put("canManage", canManage());
    status.put("health", metricsService.getHealth());
    return Response.ok(status).build();
  }

  @GET
  @Path("/events")
  public Response getAdminEventsList() {
    if (!canView()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return Response.ok(eventService.listEvents()).build();
  }

  @GET
  @Path("/cfp")
  public Response cfpSubmissions() {
    if (!canView()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    java.util.List<com.scanales.homedir.model.Event> events = eventService.listEvents();
    java.util.List<com.scanales.homedir.cfp.CfpSubmission> allSubmissions =
        new java.util.ArrayList<>();
    for (com.scanales.homedir.model.Event event : events) {
      allSubmissions.addAll(
          cfpSubmissionService.listByEventAll(
              event.getId(),
              java.util.Optional.empty(),
              com.scanales.homedir.cfp.CfpSubmissionService.SortOrder.CREATED_DESC));
    }
    return Response.ok(allSubmissions).build();
  }

  @GET
  @Path("/volunteers")
  public Response volunteers() {
    if (!canView()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    java.util.List<com.scanales.homedir.model.Event> events = eventService.listEvents();
    java.util.List<com.scanales.homedir.volunteers.VolunteerApplication> allVolunteers =
        new java.util.ArrayList<>();
    for (com.scanales.homedir.model.Event event : events) {
      allVolunteers.addAll(
          volunteerApplicationService.listByEvent(
              event.getId(),
              java.util.Optional.empty(),
              com.scanales.homedir.volunteers.VolunteerApplicationService.SortOrder.CREATED_DESC,
              Integer.MAX_VALUE,
              0));
    }
    return Response.ok(allVolunteers).build();
  }

  @GET
  @Path("/volunteers/export/{eventId}.csv")
  @Produces("text/csv")
  public Response exportVolunteersCsv(@jakarta.ws.rs.PathParam("eventId") String eventId) {
    if (!canView()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    java.util.List<com.scanales.homedir.volunteers.VolunteerApplication> volunteers =
        volunteerApplicationService.listByEvent(
            eventId,
            java.util.Optional.empty(),
            com.scanales.homedir.volunteers.VolunteerApplicationService.SortOrder.CREATED_DESC,
            Integer.MAX_VALUE,
            0);

    StringBuilder csv = new StringBuilder();
    // CSV Header
    csv.append("ID,User ID,Nombre,Email,Estado,Sobre mí,Razón para unirse,Diferenciador,Rating,Notas de revisión,Fecha creación,Fecha actualización\n");

    for (com.scanales.homedir.volunteers.VolunteerApplication app : volunteers) {
      csv.append(escapeCsv(app.id())).append(",");
      csv.append(escapeCsv(app.applicantUserId())).append(",");
      csv.append(escapeCsv(app.applicantName())).append(",");
      csv.append(escapeCsv(getApplicantEmail(app.applicantUserId()))).append(",");
      csv.append(escapeCsv(app.status().name())).append(",");
      csv.append(escapeCsv(app.aboutMe())).append(",");
      csv.append(escapeCsv(app.joinReason())).append(",");
      csv.append(escapeCsv(app.differentiator())).append(",");
      csv.append(app.ratingProfile() != null ? app.ratingProfile().toString() : "").append(",");
      csv.append(escapeCsv(app.moderationNote())).append(",");
      csv.append(app.createdAt() != null ? app.createdAt().toString() : "").append(",");
      csv.append(app.updatedAt() != null ? app.updatedAt().toString() : "").append("\n");
    }

    return Response.ok(csv.toString())
        .header("Content-Disposition", "attachment; filename=\"volunteers-" + eventId + ".csv\"")
        .build();
  }

  private String escapeCsv(String value) {
    if (value == null) return "";
    String escaped = value.replace("\"", "\"\"");
    if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
      return "\"" + escaped + "\"";
    }
    return escaped;
  }

  private String getApplicantEmail(String userId) {
    try {
      var profile = userProfileService.find(userId);
      return profile.map(com.scanales.homedir.model.UserProfile::getEmail).orElse("N/A");
    } catch (Exception e) {
      return "N/A";
    }
  }

  @GET
  @Path("/metrics")
  public Response metrics() {
    if (!canView()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return Response.ok(metricsService.getSummary()).build();
  }

  @GET
  @Path("/users")
  public Response listUsers() {
    if (!canView()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return Response.ok(new java.util.ArrayList<>(userProfileService.allProfiles().values()))
        .build();
  }

  @GET
  @Path("/users/{userId}")
  public Response getUser(@jakarta.ws.rs.PathParam("userId") String userId) {
    if (!canView()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return userProfileService
        .find(userId)
        .map(profile -> Response.ok(profile).build())
        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
  }

  @jakarta.ws.rs.POST
  @Path("/users/{userId}/xp")
  public Response addXp(
      @jakarta.ws.rs.PathParam("userId") String userId, java.util.Map<String, Object> body) {
    if (!canManage()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    if (body == null) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    int amount = 0;
    Object amountObj = body.get("amount");
    if (amountObj instanceof Number) {
      amount = ((Number) amountObj).intValue();
    }
    String reason = (String) body.getOrDefault("reason", "Admin remote operation");
    String questClassStr = (String) body.get("questClass");
    com.scanales.homedir.model.QuestClass qc = null;
    if (questClassStr != null && !questClassStr.isBlank()) {
      qc = com.scanales.homedir.model.QuestClass.valueOf(questClassStr);
    }
    com.scanales.homedir.model.UserProfile profile =
        userProfileService.addXp(userId, amount, reason, qc);
    return Response.ok(profile).build();
  }

  @jakarta.ws.rs.POST
  @Path("/users/{userId}/quest-class")
  public Response updateQuestClass(
      @jakarta.ws.rs.PathParam("userId") String userId, java.util.Map<String, String> body) {
    if (!canManage()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    if (body == null) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    String classStr = body.get("questClass");
    if (classStr == null) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    com.scanales.homedir.model.QuestClass questClass =
        com.scanales.homedir.model.QuestClass.valueOf(classStr);
    com.scanales.homedir.model.UserProfile profile =
        userProfileService.updateQuestClass(userId, questClass);
    return Response.ok(profile).build();
  }
}
