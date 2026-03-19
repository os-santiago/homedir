package com.scanales.homedir.private_;

import com.scanales.homedir.insights.DevelopmentInsightsEvent;
import com.scanales.homedir.insights.DevelopmentInsightsLedgerService;
import com.scanales.homedir.insights.InitiativeSummary;
import com.scanales.homedir.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;

/** Admin API for development insights ledger operations. */
@Path("/api/private/admin/insights")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminInsightsApiResource {

  public record StartInitiativeRequest(
      String initiativeId,
      String title,
      String definitionStartedAt,
      Map<String, String> metadata) {
  }

  public record AppendEventRequest(String initiativeId, String type, Map<String, String> metadata) {
  }

  public record ErrorPayload(String error, String message) {
  }

  @Inject SecurityIdentity identity;

  @Inject DevelopmentInsightsLedgerService insightsLedger;

  @GET
  @Path("status")
  public Response status() {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return Response.ok(insightsLedger.status()).build();
  }

  @GET
  @Path("initiatives")
  public Response initiatives(
      @QueryParam("limit") @DefaultValue("50") int limit,
      @QueryParam("offset") @DefaultValue("0") int offset) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    int safeLimit = Math.max(1, Math.min(limit, 200));
    int safeOffset = Math.max(0, Math.min(offset, 20000));
    return Response.ok(insightsLedger.listInitiatives(safeLimit, safeOffset)).build();
  }

  @GET
  @Path("initiatives/export.csv")
  @Produces("text/csv; charset=UTF-8")
  public Response exportInitiativesCsv(
      @QueryParam("limit") @DefaultValue("2000") int limit,
      @QueryParam("offset") @DefaultValue("0") int offset) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    int safeLimit = Math.max(1, Math.min(limit, 10000));
    int safeOffset = Math.max(0, Math.min(offset, 20000));
    String csv = buildCsv(insightsLedger.listInitiatives(safeLimit, safeOffset));
    return Response.ok(csv, "text/csv; charset=UTF-8")
        .header(
            "Content-Disposition",
            "attachment; filename=\"insights-initiatives-" + Instant.now().toEpochMilli() + ".csv\"")
        .build();
  }

  @POST
  @Path("initiatives/start")
  public Response startInitiative(StartInitiativeRequest request) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    if (request == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorPayload("invalid_request", "Request body is required"))
          .build();
    }
    try {
      DevelopmentInsightsEvent event =
          insightsLedger.startInitiative(
              request.initiativeId(), request.title(), request.definitionStartedAt(), request.metadata());
      return Response.status(Response.Status.CREATED).entity(event).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorPayload("invalid_payload", e.getMessage()))
          .build();
    } catch (IllegalStateException e) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(new ErrorPayload("insights_unavailable", e.getMessage()))
          .build();
    }
  }

  @POST
  @Path("events")
  public Response appendEvent(AppendEventRequest request) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    if (request == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorPayload("invalid_request", "Request body is required"))
          .build();
    }
    try {
      DevelopmentInsightsEvent event =
          insightsLedger.append(request.initiativeId(), request.type(), request.metadata());
      return Response.status(Response.Status.CREATED).entity(event).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorPayload("invalid_payload", e.getMessage()))
          .build();
    } catch (IllegalStateException e) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(new ErrorPayload("insights_unavailable", e.getMessage()))
          .build();
    }
  }

  private static String buildCsv(java.util.List<InitiativeSummary> rows) {
    StringBuilder csv = new StringBuilder(4096);
    csv.append(
        "initiative_id,title,state,started_at,definition_started_at,pr_opened_at,pr_merged_at,production_verified_at,lead_hours_to_merge,lead_hours_to_production,last_event_at,last_event_type,total_events\n");
    for (InitiativeSummary row : rows) {
      csv.append(escapeCsv(row.initiativeId())).append(',');
      csv.append(escapeCsv(row.title())).append(',');
      csv.append(escapeCsv(row.state())).append(',');
      csv.append(escapeCsv(toIso(row.startedAt()))).append(',');
      csv.append(escapeCsv(toIso(row.definitionStartedAt()))).append(',');
      csv.append(escapeCsv(toIso(row.prOpenedAt()))).append(',');
      csv.append(escapeCsv(toIso(row.prMergedAt()))).append(',');
      csv.append(escapeCsv(toIso(row.productionVerifiedAt()))).append(',');
      csv.append(escapeCsv(toStringOrEmpty(row.leadHoursToMerge()))).append(',');
      csv.append(escapeCsv(toStringOrEmpty(row.leadHoursToProduction()))).append(',');
      csv.append(escapeCsv(toIso(row.lastEventAt()))).append(',');
      csv.append(escapeCsv(row.lastEventType())).append(',');
      csv.append(row.totalEvents()).append('\n');
    }
    return csv.toString();
  }

  private static String toIso(Instant instant) {
    return instant == null ? "" : instant.toString();
  }

  private static String toStringOrEmpty(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static String escapeCsv(String raw) {
    if (raw == null) {
      return "";
    }
    boolean needsQuotes =
        raw.indexOf(',') >= 0
            || raw.indexOf('"') >= 0
            || raw.indexOf('\n') >= 0
            || raw.indexOf('\r') >= 0;
    if (!needsQuotes) {
      return raw;
    }
    return "\"" + raw.replace("\"", "\"\"") + "\"";
  }
}
