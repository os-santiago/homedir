package com.scanales.eventflow.private_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.util.AdminUtils;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.MultipartForm;

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

    @Inject
    EventService eventService;

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
                              @FormParam("days") int days) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        var now = java.time.LocalDateTime.now();
        String id = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now);
        Event event = new Event(id, title, description, days, now, identity.getAttribute("email"));
        eventService.saveEvent(event);
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
                                @FormParam("days") int days) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Event event = eventService.getEvent(id);
        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        event.setTitle(title);
        event.setDescription(description);
        event.setDays(days);
        eventService.saveEvent(event);
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
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        try {
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            String json = mapper.writeValueAsString(event);
            LOG.infov("Exporting event {0}", id);
            return Response.ok(json)
                    .header("Content-Disposition", "attachment; filename=evento_" + id + ".json")
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to export event", e);
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
                                 @FormParam("location") String location) {
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

    public static class ImportForm {
        @RestForm("file")
        FileUpload file;
    }

    @POST
    @Path("import")
    @Authenticated
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importEvent(@MultipartForm ImportForm form) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (form == null || form.file == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (form.file.contentType() == null || !form.file.contentType().contains("json")) {
            LOG.warn("Uploaded file is not JSON");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            java.nio.file.Path path = form.file.uploadedFile();
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(java.nio.file.Files.newInputStream(path));

            if (!root.hasNonNull("id")) {
                LOG.warn("Imported JSON missing id field");
                return Response.status(Response.Status.BAD_REQUEST)
                        .header("Location", "/private/admin/events?msg=Importaci%C3%B3n+fallida")
                        .build();
            }

            String id = root.get("id").asText();
            if (eventService.getEvent(id) != null) {
                return Response.status(Response.Status.SEE_OTHER)
                        .header("Location", "/private/admin/events?msg=El+evento+con+ID+" + id + "+ya+existe.+Importaci%C3%B3n+cancelada.")
                        .build();
            }

            Event event = mapper.treeToValue(root, Event.class);

            fillDefaults(event);

            eventService.saveEvent(event);
            LOG.infov("Imported event {0}", id);
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location", "/private/admin/events?msg=Todos+los+campos+fueron+exitosamente+cargados.")
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to import event", e);
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location", "/private/admin/events?msg=Importaci%C3%B3n+fallida")
                    .build();
        }
    }

    private void fillDefaults(Event event) {
        if (event.getTitle() == null) event.setTitle("VACIO");
        if (event.getDescription() == null) event.setDescription("VACIO");
        if (event.getMapUrl() == null) event.setMapUrl("VACIO");
        if (event.getCreator() == null) event.setCreator("VACIO");
        if (event.getCreatedAt() == null) event.setCreatedAt(java.time.LocalDateTime.now());

        if (event.getScenarios() != null) {
            for (Scenario sc : event.getScenarios()) {
                if (sc.getName() == null) sc.setName("VACIO");
                if (sc.getFeatures() == null) sc.setFeatures("VACIO");
                if (sc.getLocation() == null) sc.setLocation("VACIO");
                if (sc.getId() == null) sc.setId(java.util.UUID.randomUUID().toString());
            }
        }

        if (event.getAgenda() != null) {
            for (Talk t : event.getAgenda()) {
                if (t.getName() == null) t.setName("VACIO");
                if (t.getDescription() == null) t.setDescription("VACIO");
                if (t.getLocation() == null) t.setLocation("VACIO");
                if (t.getStartTime() == null) t.setStartTime(java.time.LocalTime.MIDNIGHT);
                if (t.getSpeaker() == null) t.setSpeaker(new com.scanales.eventflow.model.Speaker("","VACIO"));
            }
        }
    }
}
