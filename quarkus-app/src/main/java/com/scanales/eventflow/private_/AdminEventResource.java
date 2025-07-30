package com.scanales.eventflow.private_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.EventLoaderService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.util.AdminUtils;
import com.scanales.eventflow.util.EventUtils;
import org.jboss.logging.Logger;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Consumes;


@Path("/private/admin/events")
public class AdminEventResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance list(java.util.List<Event> events, String message);
        static native TemplateInstance edit(Event event);
    }

    @Inject
    SecurityIdentity identity;

    private static final Logger LOG = Logger.getLogger(AdminEventResource.class);
    private static final String PREFIX = "[EVENT] ";

    @Inject
    EventService eventService;

    @Inject
    EventLoaderService gitSync;

    private boolean isAdmin() {
        return AdminUtils.isAdmin(identity);
    }

    @GET
    @Path("")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response listEvents(@QueryParam("msg") String message) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        var events = eventService.listEvents();
        return Response.ok(Templates.list(events, message)).build();
    }

    @GET
    @Path("new")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response create() {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(Templates.edit(new Event())).build();
    }

    @GET
    @Path("{id}/edit")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response edit(@PathParam("id") String id) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Event event = eventService.getEvent(id);
        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(Templates.edit(event)).build();
    }

    @POST
    @Path("new")
    @Authenticated
    public Response saveEvent(@FormParam("title") String title,
                              @FormParam("description") String description,
                              @FormParam("mapUrl") String mapUrl,
                              @FormParam("days") int days,
                              @FormParam("eventDate") String eventDateStr) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (eventDateStr == null || eventDateStr.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fecha requerida").build();
        }
        var now = java.time.LocalDateTime.now();
        String id = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now);
        Event event = new Event(id, title, description, days, now, identity.getAttribute("email"));
        event.setMapUrl(mapUrl);
        event.setEventDate(java.time.LocalDate.parse(eventDateStr));
        eventService.saveEvent(event);
        gitSync.exportAndPushEvent(event, "Add event " + id);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/private/admin/events")
                .build();
    }

    @POST
    @Path("{id}/edit")
    @Authenticated
    public Response updateEvent(@PathParam("id") String id,
                                @FormParam("title") String title,
                                @FormParam("description") String description,
                                @FormParam("mapUrl") String mapUrl,
                                @FormParam("days") int days,
                                @FormParam("eventDate") String eventDateStr) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Event event = eventService.getEvent(id);
        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (eventDateStr == null || eventDateStr.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Fecha requerida").build();
        }
        event.setTitle(title);
        event.setDescription(description);
        event.setDays(days);
        event.setMapUrl(mapUrl);
        event.setEventDate(java.time.LocalDate.parse(eventDateStr));
        eventService.saveEvent(event);
        gitSync.exportAndPushEvent(event, "Update event " + id);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/event/" + id)
                .build();
    }

    @POST
    @Path("{id}/delete")
    @Authenticated
    public Response deleteEvent(@PathParam("id") String id) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        eventService.deleteEvent(id);
        gitSync.removeEvent(id, "Delete event " + id);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/private/admin/events")
                .build();
    }

    @GET
    @Path("{id}/export")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportEvent(@PathParam("id") String id) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Event event = eventService.getEvent(id);
        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("\u274c Error: Evento no encontrado.")
                    .type(MediaType.TEXT_PLAIN + ";charset=UTF-8")
                    .build();
        }
        if (!hasRequiredData(event)) {
            LOG.warnf(PREFIX + "AdminEventResource.exportEvent(): Event %s has no data to export", id);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("\u274c Error: El evento no contiene datos para exportar.")
                    .type(MediaType.TEXT_PLAIN + ";charset=UTF-8")
                    .build();
        }
        try (Jsonb jsonb = JsonbBuilder.create()) {
            String json = jsonb.toJson(event);
            LOG.infof(PREFIX + "AdminEventResource.exportEvent(): Exportando evento %s a JSON", id);
            if (!"{}".equals(json.trim())) {
                LOG.debug(PREFIX + json);
            }
            return Response.ok(json, MediaType.APPLICATION_JSON_TYPE)
                    .header("Content-Disposition", "attachment; filename=event-" + id + ".json")
                    .build();
        } catch (jakarta.json.bind.JsonbException e) {
            LOG.error(PREFIX + "AdminEventResource.exportEvent(): Error exportando evento", e);
            return Response.serverError().build();
        } catch (Exception e) {
            LOG.error(PREFIX + "AdminEventResource.exportEvent(): Error cerrando recurso", e);
            return Response.serverError().build();
        }
    }

    @POST
    @Path("{id}/scenario")
    @Authenticated
    public Response saveScenario(@PathParam("id") String eventId,
                                 @FormParam("scenarioId") String scenarioId,
                                 @FormParam("name") String name,
                                 @FormParam("features") String features,
                                 @FormParam("location") String location,
                                 @FormParam("mapUrl") String mapUrl) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (scenarioId == null || scenarioId.isBlank()) {
            var ts = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    .format(java.time.LocalDateTime.now());
            scenarioId = eventId + "-sala-" + ts;
        }
        Scenario scenario = new Scenario(scenarioId, name);
        scenario.setFeatures(features);
        scenario.setLocation(location);
        scenario.setMapUrl(mapUrl);
        eventService.saveScenario(eventId, scenario);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/private/admin/events/" + eventId + "/edit")
                .build();
    }

    @POST
    @Path("{id}/scenario/{scenarioId}/delete")
    @Authenticated
    public Response deleteScenario(@PathParam("id") String eventId,
                                   @PathParam("scenarioId") String scenarioId) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        eventService.deleteScenario(eventId, scenarioId);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/private/admin/events/" + eventId + "/edit")
                .build();
    }

    @POST
    @Path("{id}/talk")
    @Authenticated
    public Response saveTalk(@PathParam("id") String eventId,
                             @FormParam("talkId") String talkId,
                             @FormParam("name") String name,
                             @FormParam("description") String description,
                             @FormParam("location") String location,
                             @FormParam("startTime") String startTime,
                             @FormParam("duration") int duration,
                             @FormParam("day") int day) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (talkId == null || talkId.isBlank()) {
            var ts = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    .format(java.time.LocalDateTime.now());
            talkId = eventId + "-charla-" + ts;
        }
        Event event = eventService.getEvent(eventId);
        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (day < 1 || day > event.getDays()) {
            day = 1;
        }
        Talk talk = new Talk(talkId, name);
        talk.setDescription(description);
        talk.setLocation(location);
        talk.setStartTimeStr(startTime);
        talk.setDurationMinutes(duration);
        talk.setDay(day);
        eventService.saveTalk(eventId, talk);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/private/admin/events/" + eventId + "/edit")
                .build();
    }

    @POST
    @Path("{id}/talk/{talkId}/delete")
    @Authenticated
    public Response deleteTalk(@PathParam("id") String eventId,
                               @PathParam("talkId") String talkId) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        eventService.deleteTalk(eventId, talkId);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/private/admin/events/" + eventId + "/edit")
                .build();
    }

    @POST
    @Path("import")
    @Authenticated
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Response importEvent(@FormParam("file") FileUpload file) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (file == null) {
            LOG.warn(PREFIX + "AdminEventResource.importEvent(): No file received");
            var events = eventService.listEvents();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Templates.list(events, "Importaci\u00f3n fallida: archivo requerido"))
                    .build();
        }

        LOG.infof(PREFIX + "AdminEventResource.importEvent(): Intentando importar archivo %s", file.fileName());

        if (file.contentType() == null || !file.contentType().equals("application/json")) {
            LOG.warnf(PREFIX + "AdminEventResource.importEvent(): Invalid MIME type: %s", file.contentType());
            var events = eventService.listEvents();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Templates.list(events, "Importaci\u00f3n fallida: solo se aceptan archivos JSON"))
                    .build();
        }

        try (Jsonb jsonb = JsonbBuilder.create()) {
            java.nio.file.Path path = file.uploadedFile();
            Event event;
            try (var in = java.nio.file.Files.newInputStream(path)) {
                event = jsonb.fromJson(in, Event.class);
            }

            if (event.getId() == null || event.getId().isBlank()) {
                LOG.warn(PREFIX + "AdminEventResource.importEvent(): JSON sin campo id");
                var events = eventService.listEvents();
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Templates.list(events, "Importaci\u00f3n fallida: JSON sin campo id"))
                        .build();
            }

            String id = event.getId();
            if (eventService.getEvent(id) != null) {
                LOG.errorf(PREFIX + "AdminEventResource.importEvent(): Evento con ID %s ya existe", id);
                var events = eventService.listEvents();
                return Response.status(Response.Status.CONFLICT)
                        .entity(Templates.list(events, "Importaci\u00f3n fallida: el evento ya existe"))
                        .build();
            }

            EventUtils.fillDefaults(event);

            eventService.saveEvent(event);
            gitSync.exportAndPushEvent(event, "Import event " + id);
            LOG.infov("Imported event {0}", id);
            LOG.infof(PREFIX + "AdminEventResource.importEvent(): Evento %s importado correctamente", id);
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location", "/private/admin/events?msg=Importaci%C3%B3n+exitosa")
                    .build();
        } catch (java.io.IOException | jakarta.json.bind.JsonbException e) {
            LOG.error(PREFIX + "AdminEventResource.importEvent(): Error al importar evento", e);
            var events = eventService.listEvents();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Templates.list(events, "Importaci\u00f3n fallida: JSON inv\u00e1lido"))
                    .build();
        } catch (Exception e) {
            LOG.error(PREFIX + "AdminEventResource.importEvent(): Error cerrando recurso", e);
            var events = eventService.listEvents();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Templates.list(events, "Importaci\u00f3n fallida: error interno"))
                    .build();
        }
    }

    private boolean hasRequiredData(Event event) {
        if (event == null) {
            return false;
        }

        JsonbConfig cfg = new JsonbConfig().withFormatting(true);
        try (Jsonb jsonb = JsonbBuilder.create(cfg)) {
            String eventJson = jsonb.toJson(event);
            LOG.debug(PREFIX + "AdminEventResource.hasRequiredData(): contenido del evento\n" + eventJson);
        } catch (jakarta.json.bind.JsonbException e) {
            LOG.warn(PREFIX + "AdminEventResource.hasRequiredData(): No se pudo serializar evento", e);
        } catch (Exception e) {
            LOG.warn(PREFIX + "AdminEventResource.hasRequiredData(): Error cerrando recurso", e);
        }

        boolean hasLists = (event.getScenarios() != null && !event.getScenarios().isEmpty())
                || (event.getAgenda() != null && !event.getAgenda().isEmpty());

        return event.getId() != null && !event.getId().isBlank() && hasLists;
    }

}
