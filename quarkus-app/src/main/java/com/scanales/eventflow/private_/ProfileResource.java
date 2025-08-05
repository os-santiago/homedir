package com.scanales.eventflow.private_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;

import java.util.Optional;
import org.jboss.logging.Logger;

import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UserScheduleService;
import com.scanales.eventflow.model.Talk;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/private/profile")
public class ProfileResource {

    private static final Logger LOG = Logger.getLogger(ProfileResource.class);

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance profile(String name,
                String givenName,
                String familyName,
                String email,
                String sub,
                java.util.List<EventGroup> groups);
    }

    /** Helper record containing a talk and its parent event. */
    public record TalkEntry(Talk talk, com.scanales.eventflow.model.Event event) {}

    /** Talks grouped by day within an event. */
    public record DayGroup(int day, java.util.List<Talk> talks) {}

    /** Talks grouped by event. */
    public record EventGroup(com.scanales.eventflow.model.Event event,
            java.util.List<DayGroup> days) {}

    @Inject
    SecurityIdentity identity;

    @Inject
    EventService eventService;

    @Inject
    UserScheduleService userSchedule;

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance profile() {
        identity.getAttributes().forEach((k, v) -> LOG.infov("{0} = {1}", k, v));

        String name = getClaim("name");
        String givenName = getClaim("given_name");
        String familyName = getClaim("family_name");
        String email = getClaim("email");

        if (name == null) {
            name = identity.getPrincipal().getName();
        }

        String sub = getClaim("sub");
        if (sub == null) {
            sub = identity.getPrincipal().getName();
        }

        if (email == null) {
            email = sub;
        }

        var talkIds = userSchedule.getTalksForUser(email);
        java.util.List<TalkEntry> entries = talkIds.stream()
                .map(tid -> {
                    Talk t = eventService.findTalk(tid);
                    if (t == null) return null;
                    var e = eventService.findEventByTalk(tid);
                    return new TalkEntry(t, e);
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        // Group talks by event and day
        java.util.Map<com.scanales.eventflow.model.Event, java.util.Map<Integer, java.util.List<Talk>>> grouped =
                new java.util.LinkedHashMap<>();
        for (TalkEntry te : entries) {
            grouped.computeIfAbsent(te.event, k -> new java.util.TreeMap<>())
                    .computeIfAbsent(te.talk.getDay(), k -> new java.util.ArrayList<>())
                    .add(te.talk);
        }
        java.util.List<EventGroup> groups = grouped.entrySet().stream()
                .map(ev -> new EventGroup(ev.getKey(),
                        ev.getValue().entrySet().stream()
                                .map(d -> new DayGroup(d.getKey(), d.getValue()))
                                .toList()))
                .toList();

        return Templates.profile(name, givenName, familyName, email, sub, groups);
    }

    @GET
    @Path("add/{id}")
    @Authenticated
    public Response addTalkRedirect(@PathParam("id") String id) {
        String email = getEmail();
        userSchedule.addTalkForUser(email, id);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/talk/" + id)
                .build();
    }

    @POST
    @Path("add/{id}")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Response addTalk(@PathParam("id") String id) {
        String email = getEmail();
        boolean added = userSchedule.addTalkForUser(email, id);
        String status = added ? "added" : "exists";
        return Response.ok(java.util.Map.of("status", status)).build();
    }

    @GET
    @Path("remove/{id}")
    @Authenticated
    public Response removeTalkRedirect(@PathParam("id") String id) {
        String email = getEmail();
        userSchedule.removeTalkForUser(email, id);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/private/profile")
                .build();
    }

    @POST
    @Path("remove/{id}")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeTalk(@PathParam("id") String id) {
        String email = getEmail();
        boolean removed = userSchedule.removeTalkForUser(email, id);
        String status = removed ? "removed" : "missing";
        return Response.ok(java.util.Map.of("status", status)).build();
    }

    private String getClaim(String claimName) {
        Object value = null;
        if (identity.getPrincipal() instanceof OidcJwtCallerPrincipal oidc) {
            value = oidc.getClaim(claimName);
        }
        if (value == null) {
            value = identity.getAttribute(claimName);
        }
        return Optional.ofNullable(value).map(Object::toString).orElse(null);
    }

    private String getEmail() {
        String email = getClaim("email");
        if (email == null) {
            email = identity.getPrincipal().getName();
        }
        return email;
    }
}
