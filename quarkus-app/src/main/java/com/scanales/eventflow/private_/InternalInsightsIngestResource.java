package com.scanales.eventflow.private_;

import com.scanales.eventflow.insights.DevelopmentInsightsEvent;
import com.scanales.eventflow.insights.DevelopmentInsightsLedgerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Internal ingestion surface for CI/webhook automation.
 *
 * <p>Disabled by default and protected with a shared secret key.
 */
@Path("/api/internal/insights")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InternalInsightsIngestResource {

  @ConfigProperty(name = "insights.ingest.enabled", defaultValue = "false")
  boolean ingestEnabled;

  @ConfigProperty(name = "insights.ingest.key", defaultValue = "")
  String ingestKey;

  @Inject DevelopmentInsightsLedgerService insightsLedger;

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

  @POST
  @Path("initiatives/start")
  public Response startInitiative(
      @HeaderParam("X-Insights-Key") String providedKey, StartInitiativeRequest request) {
    if (!ingestEnabled) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    if (!isAuthorized(providedKey)) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(new ErrorPayload("unauthorized", "invalid_ingest_key"))
          .build();
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
      return Response.status(Response.Status.ACCEPTED).entity(event).build();
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
  public Response appendEvent(
      @HeaderParam("X-Insights-Key") String providedKey, AppendEventRequest request) {
    if (!ingestEnabled) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    if (!isAuthorized(providedKey)) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(new ErrorPayload("unauthorized", "invalid_ingest_key"))
          .build();
    }
    if (request == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorPayload("invalid_request", "Request body is required"))
          .build();
    }
    try {
      DevelopmentInsightsEvent event =
          insightsLedger.append(request.initiativeId(), request.type(), request.metadata());
      return Response.status(Response.Status.ACCEPTED).entity(event).build();
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

  private boolean isAuthorized(String providedKey) {
    if (ingestKey == null || ingestKey.isBlank() || providedKey == null || providedKey.isBlank()) {
      return false;
    }
    byte[] expected = ingestKey.getBytes(StandardCharsets.UTF_8);
    byte[] provided = providedKey.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expected, provided);
  }
}
