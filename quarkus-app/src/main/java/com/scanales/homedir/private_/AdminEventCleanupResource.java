package com.scanales.homedir.private_;

import com.scanales.homedir.config.AppMessages;
import com.scanales.homedir.model.Event;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.util.AdminUtils;
import io.quarkus.qute.i18n.MessageBundles;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/**
 * Temporary admin endpoint to clean corrupted event data.
 *
 * <p>This resource provides utilities to fix data corruption issues that cannot be resolved through
 * normal admin UI.
 */
@Path("/private/admin/events/cleanup")
public class AdminEventCleanupResource {

  @Inject SecurityIdentity identity;

  @Inject EventService eventService;

  private boolean canManageAdminBackoffice() {
    return AdminUtils.canManageAdminBackoffice(identity);
  }

  /**
   * Clear event agenda to force re-seeding.
   *
   * <p>Use this when event agenda has corrupted data (e.g., phantom talks that don't exist in admin
   * but appear in public view).
   *
   * <p>The agenda will be regenerated automatically on next save/load if the event has seeding
   * logic in EventService.
   */
  @POST
  @Path("{id}/clear-agenda")
  @Authenticated
  public Response clearAgenda(@PathParam("id") String eventId) {
    if (!canManageAdminBackoffice()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    Event event = eventService.getEvent(eventId);
    if (event == null) {
      AppMessages i18n = MessageBundles.get(AppMessages.class);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(Map.of("error", i18n.admin_cleanup_event_not_found()))
          .build();
    }

    int agendaSize = event.getAgenda() != null ? event.getAgenda().size() : 0;

    // Clear agenda
    event.setAgenda(new java.util.ArrayList<>());

    // Save - this will trigger re-seeding if event has seeding logic
    eventService.saveEvent(event);

    AppMessages i18n = MessageBundles.get(AppMessages.class);
    return Response.ok()
        .entity(
            Map.of(
                "success",
                true,
                "message",
                i18n.admin_cleanup_agenda_cleared(),
                "eventId",
                eventId,
                "previousAgendaSize",
                agendaSize))
        .build();
  }
}
