package com.scanales.eventflow.private_;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.TalkInfo;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UserScheduleService;
import com.scanales.eventflow.service.UserScheduleService.TalkDetails;

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
 * Compact view of the talks registered by the current user grouped by event.
 */
@Path("/private/my-talks")
public class MyTalksResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance myTalks(List<EventGroup> events,
                Map<String, TalkDetails> info);
    }

    /** Talks grouped by event. */
    public record EventGroup(Event event, List<TalkInfo> talks) {}

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
        var info = userSchedule.getTalkDetailsForUser(email);
        var talkIds = info.keySet();
        List<TalkInfo> entries = talkIds.stream()
                .map(eventService::findTalkInfo)
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator
                        .comparing((TalkInfo ti) -> ti.event().getTitle(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ti -> ti.talk().getDay())
                        .thenComparing(ti -> ti.talk().getStartTime(),
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();

        Map<String, EventGroup> grouped = new LinkedHashMap<>();
        for (TalkInfo ti : entries) {
            Event ev = ti.event();
            grouped.computeIfAbsent(ev.getId(), id -> new EventGroup(ev, new ArrayList<>()))
                    .talks().add(ti);
        }

        return Templates.myTalks(new ArrayList<>(grouped.values()), info);
    }

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

