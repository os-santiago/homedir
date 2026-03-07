package com.scanales.eventflow.private_;

import com.scanales.eventflow.insights.DevelopmentInsightsEvent;
import com.scanales.eventflow.insights.DevelopmentInsightsLedgerService;
import com.scanales.eventflow.util.AdminUtils;
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
import java.util.Map;

/**
 * Hidden admin API for development insights ledger. Iteration 1 exposes foundational endpoints
 * without frontend consumption.
 */
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
  public Response initiatives(@QueryParam("limit") @DefaultValue("50") int limit) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    int safeLimit = Math.max(1, Math.min(limit, 200));
    return Response.ok(insightsLedger.listInitiatives(safeLimit)).build();
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
}

