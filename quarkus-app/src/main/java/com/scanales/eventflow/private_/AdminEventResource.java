package com.scanales.eventflow.private_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.SpeakerService;
import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.util.AdminUtils;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
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
        static native TemplateInstance edit(Event event, java.util.List<Speaker> speakers, String message);
    }

    @Inject
    SecurityIdentity identity;

    private static final Logger LOG = Logger.getLogger(AdminEventResource.class);

    @Inject
    EventService eventService;

    @Inject
    SpeakerService speakerService;

    @Inject
    ObjectMapper objectMapper;

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
    public Response create(@QueryParam("msg") String message) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(Templates.edit(new Event(), speakerService.listSpeakers(), message)).build();
    }

    @GET
    @Path("{id}/edit")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response edit(@PathParam("id") String id, @QueryParam("msg") String message) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Event event = eventService.getEvent(id);
        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(Templates.edit(event, speakerService.listSpeakers(), message)).build();
    }

    @POST
    @Path("new")
    @Authenticated
    public Response saveEvent(@FormParam("title") String title,
                              @FormParam("description") String description,
                              @FormParam("date") String date,
                              @FormParam("days") int days,
                              @FormParam("logoUrl") String logoUrl,
                              @FormParam("mapUrl") String mapUrl,
                              @FormParam("contactEmail") String contactEmail,
                              @FormParam("website") String website,
                              @FormParam("twitter") String twitter,
                              @FormParam("linkedin") String linkedin,
                              @FormParam("instagram") String instagram,
                              @FormParam("ticketsUrl") String ticketsUrl) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        var now = java.time.LocalDateTime.now();
        String id = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now);
        Event event = new Event(id, title, description, days, now, identity.getAttribute("email"));
        event.setDateStr(date);
        event.setLogoUrl(sanitizeUrl(logoUrl));
        event.setMapUrl(sanitizeUrl(mapUrl));
        event.setContactEmail(sanitizeEmail(contactEmail));
        event.setWebsite(sanitizeUrl(website));
        event.setTwitter(sanitizeUrl(twitter));
        event.setLinkedin(sanitizeUrl(linkedin));
        event.setInstagram(sanitizeUrl(instagram));
        event.setTicketsUrl(sanitizeUrl(ticketsUrl));
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
                                @FormParam("date") String date,
                                @FormParam("days") int days,
                                @FormParam("logoUrl") String logoUrl,
                                @FormParam("mapUrl") String mapUrl,
                                @FormParam("contactEmail") String contactEmail,
                                @FormParam("website") String website,
                                @FormParam("twitter") String twitter,
                                @FormParam("linkedin") String linkedin,
                                @FormParam("instagram") String instagram,
                                @FormParam("ticketsUrl") String ticketsUrl) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Event event = eventService.getEvent(id);
        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        event.setTitle(title);
        event.setDescription(description);
        event.setDateStr(date);
        event.setDays(days);
        event.setLogoUrl(sanitizeUrl(logoUrl));
        event.setMapUrl(sanitizeUrl(mapUrl));
        event.setContactEmail(sanitizeEmail(contactEmail));
        event.setWebsite(sanitizeUrl(website));
        event.setTwitter(sanitizeUrl(twitter));
        event.setLinkedin(sanitizeUrl(linkedin));
        event.setInstagram(sanitizeUrl(instagram));
        event.setTicketsUrl(sanitizeUrl(ticketsUrl));
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
                             @FormParam("speakerId") String speakerId,
                             @FormParam("location") String location,
                             @FormParam("startTime") String startTime,
                             @FormParam("day") int day) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Event event = eventService.getEvent(eventId);
        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (day < 1 || day > event.getDays()) {
            day = 1;
        }
        if (talkId == null || talkId.isBlank() || location == null || location.isBlank()
                || startTime == null || startTime.isBlank()) {
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location",
                            "/private/admin/events/" + eventId + "/edit?msg=Campos+obligatorios")
                    .build();
        }
        Talk base = speakerId != null && !speakerId.isBlank()
                ? speakerService.getTalk(speakerId, talkId)
                : speakerService.findTalk(talkId);
        if (base == null) {
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location",
                            "/private/admin/events/" + eventId + "/edit?msg=Charla+no+encontrada")
                    .build();
        }
        if (event.getAgenda().stream().anyMatch(t -> t.getId().equals(talkId))) {
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location",
                            "/private/admin/events/" + eventId + "/edit?msg=Asignacion+duplicada")
                    .build();
        }
        Talk talk = new Talk(talkId, base.getName());
        talk.setDescription(base.getDescription());
        talk.setDurationMinutes(base.getDurationMinutes());
        talk.setSpeakers(base.getSpeakers());
        talk.setLocation(location);
        talk.setStartTimeStr(startTime);
        talk.setDay(day);
        if (eventService.hasOverlap(eventId, talk)) {
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location",
                            "/private/admin/events/" + eventId + "/edit?msg=Horario+solapado")
                    .build();
        }
        eventService.saveTalk(eventId, talk);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/private/admin/events/" + eventId + "/edit?msg=Charla+agregada")
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
            LOG.warn("No file received");
            var events = eventService.listEvents();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Templates.list(events, "Importaci\u00f3n fallida: archivo requerido"))
                    .build();
        }

        LOG.infov("Received file {0}", file.fileName());

        if (file.contentType() == null || !file.contentType().equals("application/json")) {
            LOG.warnf("Invalid MIME type: %s", file.contentType());
            var events = eventService.listEvents();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Templates.list(events, "Importaci\u00f3n fallida: solo se aceptan archivos JSON"))
                    .build();
        }

        try {
            java.nio.file.Path path = file.uploadedFile();
            Event event;
            try (var is = java.nio.file.Files.newInputStream(path)) {
                event = objectMapper.readValue(is, Event.class);
            }

            if (event.getId() == null || event.getId().isBlank()) {
                LOG.warn("Imported JSON missing id field");
                var events = eventService.listEvents();
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Templates.list(events, "Importaci\u00f3n fallida: JSON sin campo id"))
                        .build();
            }

            String id = event.getId();
            if (eventService.getEvent(id) != null) {
                LOG.warnf("Event %s already exists", id);
                var events = eventService.listEvents();
                return Response.status(Response.Status.CONFLICT)
                        .entity(Templates.list(events, "Importaci\u00f3n fallida: el evento ya existe"))
                        .build();
            }

            fillDefaults(event);

            eventService.saveEvent(event);
            LOG.infov("Imported event {0}", id);
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location", "/private/admin/events?msg=Importaci%C3%B3n+exitosa")
                    .build();
        } catch (UnrecognizedPropertyException e) {
            LOG.error("Unknown JSON property", e);
            var events = eventService.listEvents();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Templates.list(events, "Importaci\u00f3n fallida: propiedades desconocidas"))
                    .build();
        } catch (Exception e) {
            LOG.error("Failed to import event", e);
            var events = eventService.listEvents();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Templates.list(events, "Importaci\u00f3n fallida: JSON inv\u00e1lido"))
                    .build();
        }
    }

    private String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            var u = new java.net.URL(url);
            u.toURI();
            return url;
        } catch (Exception e) {
            return null;
        }
    }

    private String sanitizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.contains("@") ? email : null;
    }

    private void fillDefaults(Event event) {
        if (event.getTitle() == null) event.setTitle("VACIO");
        if (event.getDescription() == null) event.setDescription("VACIO");
        if (event.getMapUrl() == null) event.setMapUrl("VACIO");
        if (event.getLogoUrl() == null) event.setLogoUrl("VACIO");
        if (event.getContactEmail() == null) event.setContactEmail("VACIO");
        if (event.getWebsite() == null) event.setWebsite("VACIO");
        if (event.getTwitter() == null) event.setTwitter("VACIO");
        if (event.getLinkedin() == null) event.setLinkedin("VACIO");
        if (event.getInstagram() == null) event.setInstagram("VACIO");
        if (event.getTicketsUrl() == null) event.setTicketsUrl("VACIO");
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
                if (t.getSpeakers() == null || t.getSpeakers().isEmpty())
                    t.setSpeakers(java.util.List.of(new com.scanales.eventflow.model.Speaker("","VACIO")));
            }
        }
    }
}
