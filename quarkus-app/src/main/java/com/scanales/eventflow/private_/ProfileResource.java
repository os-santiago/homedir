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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/private/profile")
public class ProfileResource {

    private static final Logger LOG = Logger.getLogger(ProfileResource.class);
    private static final String PREFIX = "[WEB] ";

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance profile(String name,
                String givenName,
                String familyName,
                String email,
                String sub,
                java.util.List<TalkEntry> talks);
    }

    /** Helper record containing a talk and its parent event. */
    public record TalkEntry(Talk talk, com.scanales.eventflow.model.Event event) {
        public String status() {
            if (talk == null || event == null) return "";
            java.time.LocalDate base = event.getEventDate();
            if (base == null) {
                base = event.getCreatedAt() == null
                        ? java.time.LocalDate.now()
                        : event.getCreatedAt().toLocalDate();
            }
            java.time.LocalDate startDate = base;
            java.time.LocalDateTime start = java.time.LocalDateTime.of(
                    startDate.plusDays(talk.getDay() - 1),
                    talk.getStartTime());
            java.time.LocalDateTime end = start.plusMinutes(talk.getDurationMinutes());
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            if (now.isBefore(start.minusMinutes(30))) return "A tiempo";
            if (now.isBefore(start)) return "Pronto a comenzar";
            if (!now.isAfter(end)) return "En curso";
            return "Finalizada";
        }
    }

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
        LOG.info(PREFIX + "Loading profile page");
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
        java.util.List<TalkEntry> talks = talkIds.stream()
                .map(tid -> {
                    Talk t = eventService.findTalk(tid);
                    if (t == null) return null;
                    var e = eventService.findEventByTalk(tid);
                    return new TalkEntry(t, e);
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return Templates.profile(name, givenName, familyName, email, sub, talks);
    }

    @GET
    @Path("add/{id}")
    @Authenticated
    public Response addTalk(@PathParam("id") String id) {
        String email = getClaim("email");
        if (email == null) {
            email = identity.getPrincipal().getName();
        }
        userSchedule.addTalkForUser(email, id);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/talk/" + id)
                .build();
    }

    @GET
    @Path("remove/{id}")
    @Authenticated
    public Response removeTalk(@PathParam("id") String id) {
        String email = getClaim("email");
        if (email == null) {
            email = identity.getPrincipal().getName();
        }
        userSchedule.removeTalkForUser(email, id);
        return Response.status(Response.Status.SEE_OTHER)
                .header("Location", "/private/profile")
                .build();
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
}
