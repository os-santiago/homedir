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

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


@Path("/private/admin/events")
public class AdminEventResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance list(java.util.List<Event> events);
        static native TemplateInstance edit(Event event);
    }

    @Inject
    SecurityIdentity identity;

    @Inject
    EventService eventService;

    private boolean isAdmin() {
        return AdminUtils.isAdmin(identity);
    }

    @GET
    @Path("")
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public Response listEvents() {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        var events = eventService.listEvents();
        return Response.ok(Templates.list(events)).build();
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
                              @FormParam("description") String description) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        var now = java.time.LocalDateTime.now();
        String id = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now);
        Event event = new Event(id, title, description, now, identity.getAttribute("email"));
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
                                @FormParam("description") String description) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Event event = eventService.getEvent(id);
        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        event.setTitle(title);
        event.setDescription(description);
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
                             @FormParam("name") String name,
                             @FormParam("description") String description,
                             @FormParam("location") String location) {
        if (!isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (talkId == null || talkId.isBlank()) {
            var ts = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    .format(java.time.LocalDateTime.now());
            talkId = eventId + "-charla-" + ts;
        }
        Talk talk = new Talk(talkId, name);
        talk.setDescription(description);
        talk.setLocation(location);
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
}
