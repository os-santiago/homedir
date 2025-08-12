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
import jakarta.ws.rs.HeaderParam;


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
                              @FormParam("timezone") String timezone,
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
        event.setTimezone(sanitizeZone(timezone));
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
                                @FormParam("timezone") String timezone,
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
        event.setTimezone(sanitizeZone(timezone));
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
                             @FormParam("day") int day,
                             @HeaderParam("X-Request-ID") String requestId) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        String reqId = requestId == null || requestId.isBlank()
                ? java.util.UUID.randomUUID().toString()
                : requestId;
        String user = identity.getAttribute("email");
        Event event = eventService.getEvent(eventId);
        if (event == null) {
            LOG.warnf("accion=charla_crear_validacion_fallida causa=evento_no_encontrado requestId=%s eventoId=%s", reqId, eventId);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (day < 1 || day > event.getDays()) {
            day = 1;
        }
        if (talkId == null || talkId.isBlank() || location == null || location.isBlank()
                || startTime == null || startTime.isBlank()) {
            LOG.warnf("accion=charla_crear_validacion_fallida causa=faltan_campos usuario=%s eventoId=%s requestId=%s", user, eventId, reqId);
            String msg = java.net.URLEncoder.encode(
                    "Campos obligatorios",
                    java.nio.charset.StandardCharsets.UTF_8);
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location",
                            "/private/admin/events/" + eventId + "/edit?msg=" + msg)
                    .build();
        }
        Talk base = speakerId != null && !speakerId.isBlank()
                ? speakerService.getTalk(speakerId, talkId)
                : speakerService.findTalk(talkId);
        if (base == null) {
            LOG.warnf("accion=charla_crear_validacion_fallida causa=charla_no_encontrada talkId=%s eventoId=%s requestId=%s", talkId, eventId, reqId);
            String msg = java.net.URLEncoder.encode(
                    "Charla no encontrada",
                    java.nio.charset.StandardCharsets.UTF_8);
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location",
                            "/private/admin/events/" + eventId + "/edit?msg=" + msg)
                    .build();
        }
        java.time.LocalTime start = java.time.LocalTime.parse(startTime);
        java.time.LocalTime end = start.plusMinutes(base.getDurationMinutes());
        LOG.infof("accion=charla_crear_intento usuario=%s eventoId=%s charlaTitulo=%s fechaInicio=%s fechaFin=%s dia=%d sala=%s requestId=%s", user, eventId, base.getName(), start, end, day, location, reqId);
        if (event.getAgenda().stream().anyMatch(t -> t.getId().equals(talkId))) {
            LOG.warnf("accion=charla_crear_rechazada motivo=duplicado charlaExistenteId=%s eventoId=%s requestId=%s", talkId, eventId, reqId);
            String msg = java.net.URLEncoder.encode(
                    "Esta charla ya existe para el evento",
                    java.nio.charset.StandardCharsets.UTF_8);
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location",
                            "/private/admin/events/" + eventId + "/edit?msg=" + msg)
                    .build();
        }
        Talk talk = new Talk(talkId, base.getName());
        talk.setDescription(base.getDescription());
        talk.setDurationMinutes(base.getDurationMinutes());
        talk.setSpeakers(base.getSpeakers());
        talk.setLocation(location);
        talk.setStartTime(start);
        talk.setDay(day);
        Talk overlap = eventService.findOverlap(eventId, talk);
        if (overlap != null) {
            LOG.warnf("accion=charla_crear_rechazada motivo=conflicto_agenda charlaExistenteId=%s eventoId=%s requestId=%s", overlap.getId(), eventId, reqId);
            String raw = String.format("No se pudo agregar: hay un solapamiento en %s dia %d %s con '%s'", location, day, start, overlap.getName());
            String msg = java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8);
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location", "/private/admin/events/" + eventId + "/edit?msg=" + msg)
                    .build();
        }
        try {
            eventService.saveTalk(eventId, talk);
            LOG.infof("accion=charla_crear_exito charlaId=%s eventoId=%s requestId=%s", talkId, eventId, reqId);
            String msg = java.net.URLEncoder.encode(
                    "✅ Charla '" + base.getName() + "' agregada al evento.",
                    java.nio.charset.StandardCharsets.UTF_8);
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location", "/private/admin/events/" + eventId + "/edit?msg=" + msg)
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "accion=charla_crear_error motivo=excepcion requestId=%s", reqId);
            String msg = java.net.URLEncoder.encode(
                    "No pudimos agregar la charla en este momento. Intenta nuevamente",
                    java.nio.charset.StandardCharsets.UTF_8);
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location", "/private/admin/events/" + eventId + "/edit?msg=" + msg)
                    .build();
        }
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
    @Path("{id}/break")
    @Authenticated
    public Response saveBreak(@PathParam("id") String eventId,
                              @FormParam("breakId") String breakId,
                              @FormParam("name") String name,
                              @FormParam("duration") int duration,
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
        if (name == null || name.isBlank() || location == null || location.isBlank()
                || startTime == null || startTime.isBlank() || duration <= 0) {
            String msg = java.net.URLEncoder.encode(
                    "Campos obligatorios",
                    java.nio.charset.StandardCharsets.UTF_8);
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location",
                            "/private/admin/events/" + eventId + "/edit?msg=" + msg)
                    .build();
        }
        Talk talk;
        if (breakId != null && !breakId.isBlank()) {
            final String existingId = breakId;
            talk = event.getAgenda().stream()
                    .filter(t -> t.getId().equals(existingId))
                    .findFirst()
                    .orElse(new Talk(existingId, name));
            talk.setName(name);
        } else {
            var ts = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    .format(java.time.LocalDateTime.now());
            breakId = eventId + "-break-" + ts;
            talk = new Talk(breakId, name);
        }
        talk.setDurationMinutes(duration);
        talk.setSpeakers(java.util.List.of());
        talk.setLocation(location);
        talk.setStartTime(java.time.LocalTime.parse(startTime));
        talk.setDay(day);
        talk.setBreak(true);
        Talk overlap = eventService.findOverlap(eventId, talk);
        if (overlap != null) {
            String raw = String.format(
                    "No se pudo agregar: hay un solapamiento en %s dia %d %s con '%s'",
                    location, day, startTime, overlap.getName());
            String msg = java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8);
            return Response.status(Response.Status.SEE_OTHER)
                    .header("Location", "/private/admin/events/" + eventId + "/edit?msg=" + msg)
                    .build();
        }
        eventService.saveTalk(eventId, talk);
        String msg = java.net.URLEncoder.encode(
                "✅ Break agregado al evento.",
                java.nio.charset.StandardCharsets.UTF_8);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/private/admin/events/" + eventId + "/edit?msg=" + msg)
                .build();
    }

    @POST
    @Path("{id}/break/{breakId}/delete")
    @Authenticated
    public Response deleteBreak(@PathParam("id") String eventId,
                                @PathParam("breakId") String breakId) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        eventService.deleteTalk(eventId, breakId);
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

    private String sanitizeZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return null;
        }
        try {
            return java.time.ZoneId.of(zone).getId();
        } catch (Exception e) {
            return null;
        }
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
        if (event.getTimezone() == null) event.setTimezone("UTC");
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
