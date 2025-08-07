package com.scanales.eventflow.private_;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.model.TalkInfo;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UserScheduleService;

import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Shows the list of talks registered by the current user grouped by event.
 */
@Path("/my-events")
public class MyEventsResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance myEvents(java.util.Collection<EventGroup> groups,
                String name,
                String email);
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
    public TemplateInstance show() {
        String email = getEmail();
        String name = getClaim("name");
        if (name == null) {
            name = identity.getPrincipal().getName();
        }

        var talkIds = userSchedule.getTalksForUser(email);
        List<TalkInfo> talks = talkIds.stream()
                .map(eventService::findTalkInfo)
                .filter(java.util.Objects::nonNull)
                .toList();

        Map<String, EventGroup> grouped = new LinkedHashMap<>();
        for (TalkInfo ti : talks) {
            Event ev = ti.event();
            grouped.computeIfAbsent(ev.getId(), id -> new EventGroup(ev, new ArrayList<>()))
                    .talks().add(ti.talk());
        }

        return Templates.myEvents(grouped.values(), name, email);
    }

    public static record EventGroup(Event event, List<Talk> talks) {}

    private String getClaim(String claimName) {
        Object value = null;
        if (identity.getPrincipal() instanceof OidcJwtCallerPrincipal oidc) {
            value = oidc.getClaim(claimName);
        }
        if (value == null) {
            value = identity.getAttribute(claimName);
        }
        return value == null ? null : value.toString();
    }

    private String getEmail() {
        String email = getClaim("email");
        if (email == null) {
            email = identity.getPrincipal().getName();
        }
        return email;
    }
}

