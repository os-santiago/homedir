package com.scanales.eventflow.private_;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UserScheduleService;

import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.web.Route;
import io.vertx.core.http.HttpMethod;

import jakarta.inject.Inject;

/**
 * Shows the list of talks registered by the current user grouped by event.
 */
public class MyEventsResource {

    @Inject
    Template myEvents;

    @Inject
    SecurityIdentity identity;

    @Inject
    EventService eventService;

    @Inject
    UserScheduleService userSchedule;

    @Route(path = "/my-events", methods = HttpMethod.GET)
    @Authenticated
    public TemplateInstance show() {
        String email = getEmail();
        String name = getClaim("name");
        if (name == null) {
            name = identity.getPrincipal().getName();
        }

        var talkIds = userSchedule.getTalksForUser(email);
        List<TalkInfo> talks = talkIds.stream()
                .map(tid -> {
                    Talk t = eventService.findTalk(tid);
                    if (t == null) return null;
                    Event e = eventService.findEventByTalk(tid);
                    return new TalkInfo(t, e);
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        Map<String, EventGroup> grouped = new LinkedHashMap<>();
        for (TalkInfo ti : talks) {
            Event ev = ti.event();
            grouped.computeIfAbsent(ev.getId(), id -> new EventGroup(ev, new ArrayList<>()))
                    .talks().add(ti.talk());
        }

        return myEvents.data("groups", grouped.values())
                .data("name", name)
                .data("email", email);
    }

    public static record TalkInfo(Talk talk, Event event) {}

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

