package com.scanales.homedir.private_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.homedir.eventops.EventActivityVisibility;
import com.scanales.homedir.eventops.EventOperationsService;
import com.scanales.homedir.eventops.EventSpace;
import com.scanales.homedir.eventops.EventSpaceActivity;
import com.scanales.homedir.eventops.EventSpaceResponsibleShift;
import com.scanales.homedir.eventops.EventSpaceType;
import com.scanales.homedir.eventops.EventStaffAssignment;
import com.scanales.homedir.eventops.EventStaffRole;
import com.scanales.homedir.util.AdminUtils;
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
import java.util.Map;
import java.util.Optional;

@Path("/api/private/admin/events/{eventId}/ops")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class AdminEventOperationsApiResource {

  @Inject EventOperationsService eventOperationsService;
  @Inject SecurityIdentity identity;

  @GET
  @Path("/staff")
  public Response listStaff(
      @PathParam("eventId") String eventId,
      @QueryParam("include_inactive") Boolean includeInactive) {
    Response unauthorized = enforceAdmin();
    if (unauthorized != null) {
      return unauthorized;
    }
    boolean includeInactiveResolved = includeInactive != null && includeInactive;
    return Response.ok(new StaffListResponse(eventOperationsService.listStaff(eventId, includeInactiveResolved)))
        .build();
  }

  @PUT
  @Path("/staff/{userId}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response upsertStaff(
      @PathParam("eventId") String eventId,
      @PathParam("userId") String userId,
      UpsertStaffRequest request) {
    Response unauthorized = enforceAdmin();
    if (unauthorized != null) {
      return unauthorized;
    }
    EventStaffRole role = EventStaffRole.fromApi(request != null ? request.role() : null).orElse(null);
    if (role == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_role")).build();
    }
    boolean active = request == null || request.active() == null || request.active();
    try {
      EventStaffAssignment updated =
          eventOperationsService.upsertStaff(
              eventId,
              userId,
              request != null ? request.userName() : null,
              role,
              request != null ? request.source() : null,
              active);
      return Response.ok(new StaffMutationResponse(updated)).build();
    } catch (EventOperationsService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (EventOperationsService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @GET
  @Path("/spaces")
  public Response listSpaces(
      @PathParam("eventId") String eventId,
      @QueryParam("include_inactive") Boolean includeInactive) {
    Response unauthorized = enforceAdmin();
    if (unauthorized != null) {
      return unauthorized;
    }
    boolean includeInactiveResolved = includeInactive != null && includeInactive;
    return Response.ok(new SpaceListResponse(eventOperationsService.listSpaces(eventId, includeInactiveResolved)))
        .build();
  }

  @PUT
  @Path("/spaces/{spaceId}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response upsertSpace(
      @PathParam("eventId") String eventId,
      @PathParam("spaceId") String spaceId,
      UpsertSpaceRequest request) {
    Response unauthorized = enforceAdmin();
    if (unauthorized != null) {
      return unauthorized;
    }
    EventSpaceType type = EventSpaceType.fromApi(request != null ? request.type() : null).orElse(null);
    try {
      EventSpace updated =
          eventOperationsService.upsertSpace(
              eventId,
              spaceId,
              request != null ? request.name() : null,
              type,
              request != null ? request.capacity() : null,
              request == null || request.active() == null || request.active());
      return Response.ok(new SpaceMutationResponse(updated)).build();
    } catch (EventOperationsService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (EventOperationsService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @GET
  @Path("/spaces/{spaceId}/shifts")
  public Response listSpaceShifts(@PathParam("eventId") String eventId, @PathParam("spaceId") String spaceId) {
    Response unauthorized = enforceAdmin();
    if (unauthorized != null) {
      return unauthorized;
    }
    return Response.ok(new ShiftListResponse(eventOperationsService.listSpaceShifts(eventId, spaceId))).build();
  }

  @POST
  @Path("/spaces/{spaceId}/shifts")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createSpaceShift(
      @PathParam("eventId") String eventId,
      @PathParam("spaceId") String spaceId,
      CreateShiftRequest request) {
    Response unauthorized = enforceAdmin();
    if (unauthorized != null) {
      return unauthorized;
    }
    try {
      EventSpaceResponsibleShift created =
          eventOperationsService.createSpaceShift(
              eventId,
              spaceId,
              request != null ? request.userId() : null,
              request != null ? request.startAt() : null,
              request != null ? request.endAt() : null);
      return Response.status(Response.Status.CREATED).entity(new ShiftMutationResponse(created)).build();
    } catch (EventOperationsService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (EventOperationsService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @GET
  @Path("/activities")
  public Response listActivities(
      @PathParam("eventId") String eventId, @QueryParam("visibility") String visibilityRaw) {
    Response unauthorized = enforceAdmin();
    if (unauthorized != null) {
      return unauthorized;
    }
    Optional<EventActivityVisibility> visibility = parseVisibility(visibilityRaw);
    if (visibilityRaw != null && !visibilityRaw.isBlank() && visibility == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "invalid_visibility"))
          .build();
    }
    return Response.ok(new ActivityListResponse(eventOperationsService.listActivities(eventId, visibility))).build();
  }

  @PUT
  @Path("/activities/{activityId}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response upsertActivity(
      @PathParam("eventId") String eventId,
      @PathParam("activityId") String activityId,
      UpsertActivityRequest request) {
    Response unauthorized = enforceAdmin();
    if (unauthorized != null) {
      return unauthorized;
    }
    EventActivityVisibility visibility =
        EventActivityVisibility.fromApi(request != null ? request.visibility() : null).orElse(null);
    try {
      EventSpaceActivity updated =
          eventOperationsService.upsertActivity(
              eventId,
              activityId,
              request != null ? request.spaceId() : null,
              request != null ? request.title() : null,
              request != null ? request.details() : null,
              visibility,
              request != null ? request.startAt() : null,
              request != null ? request.endAt() : null);
      return Response.ok(new ActivityMutationResponse(updated)).build();
    } catch (EventOperationsService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (EventOperationsService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  @GET
  @Path("/runsheet")
  public Response runSheet(
      @PathParam("eventId") String eventId,
      @QueryParam("visibility") String visibilityRaw,
      @QueryParam("include_inactive_staff") Boolean includeInactiveStaff) {
    Response unauthorized = enforceAdmin();
    if (unauthorized != null) {
      return unauthorized;
    }
    Optional<EventActivityVisibility> visibility = parseVisibility(visibilityRaw);
    if (visibilityRaw != null && !visibilityRaw.isBlank() && visibility == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(Map.of("error", "invalid_visibility"))
          .build();
    }
    try {
      EventOperationsService.EventRunSheetView runSheet =
          eventOperationsService.buildRunSheet(
              eventId,
              visibility,
              includeInactiveStaff != null && includeInactiveStaff);
      return Response.ok(new RunSheetResponse(runSheet.staff(), runSheet.spaces(), runSheet.shifts(), runSheet.activities()))
          .build();
    } catch (EventOperationsService.ValidationException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
    } catch (EventOperationsService.NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
    }
  }

  private Response enforceAdmin() {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    return null;
  }

  private static Optional<EventActivityVisibility> parseVisibility(String raw) {
    if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw.trim())) {
      return Optional.empty();
    }
    Optional<EventActivityVisibility> parsed = EventActivityVisibility.fromApi(raw);
    return parsed.isPresent() ? parsed : null;
  }

  public record UpsertStaffRequest(
      @JsonProperty("user_name") String userName,
      String role,
      String source,
      Boolean active) {}

  public record UpsertSpaceRequest(String name, String type, Integer capacity, Boolean active) {}

  public record CreateShiftRequest(
      @JsonProperty("user_id") String userId,
      @JsonProperty("start_at") Instant startAt,
      @JsonProperty("end_at") Instant endAt) {}

  public record UpsertActivityRequest(
      @JsonProperty("space_id") String spaceId,
      String title,
      String details,
      String visibility,
      @JsonProperty("start_at") Instant startAt,
      @JsonProperty("end_at") Instant endAt) {}

  public record StaffListResponse(@JsonProperty("items") List<EventStaffAssignment> items) {}

  public record StaffMutationResponse(EventStaffAssignment item) {}

  public record SpaceListResponse(@JsonProperty("items") List<EventSpace> items) {}

  public record SpaceMutationResponse(EventSpace item) {}

  public record ShiftListResponse(@JsonProperty("items") List<EventSpaceResponsibleShift> items) {}

  public record ShiftMutationResponse(EventSpaceResponsibleShift item) {}

  public record ActivityListResponse(@JsonProperty("items") List<EventSpaceActivity> items) {}

  public record ActivityMutationResponse(EventSpaceActivity item) {}

  public record RunSheetResponse(
      List<EventStaffAssignment> staff,
      List<EventSpace> spaces,
      List<EventSpaceResponsibleShift> shifts,
      List<EventSpaceActivity> activities) {}
}

