package com.scanales.eventflow.public_;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scanales.eventflow.agenda.AgendaProposalConfig;
import com.scanales.eventflow.agenda.AgendaProposalConfigService;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/api/events/{eventId}/agenda")
@Produces(MediaType.APPLICATION_JSON)
public class EventAgendaApiResource {

  @Inject AgendaProposalConfigService agendaProposalConfigService;
  @Inject EventService eventService;
  @Inject SecurityIdentity identity;

  @GET
  @Path("/config")
  public Response config(@PathParam("eventId") String eventId) {
    if (eventService.getEvent(eventId) == null) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "event_not_found")).build();
    }
    AgendaProposalConfig config = agendaProposalConfigService.current();
    return Response.ok(
            new AgendaConfigResponse(
                config.proposalNoticeEnabled(),
                AdminUtils.isAdmin(identity)))
        .build();
  }

  @PUT
  @Path("/config")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateConfig(
      @PathParam("eventId") String eventId, AgendaConfigUpdateRequest request) {
    if (eventService.getEvent(eventId) == null) {
      return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "event_not_found")).build();
    }
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "admin_required")).build();
    }
    if (request == null || request.proposalNoticeEnabled() == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "invalid_config")).build();
    }
    boolean enabled = agendaProposalConfigService.updateProposalNoticeEnabled(request.proposalNoticeEnabled());
    return Response.ok(new AgendaConfigResponse(enabled, true)).build();
  }

  public record AgendaConfigUpdateRequest(
      @JsonProperty("proposal_notice_enabled") Boolean proposalNoticeEnabled) {}

  public record AgendaConfigResponse(
      @JsonProperty("proposal_notice_enabled") boolean proposalNoticeEnabled,
      boolean admin) {}
}
