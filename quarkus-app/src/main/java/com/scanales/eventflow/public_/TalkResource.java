package com.scanales.eventflow.public_;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UserScheduleService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.model.Talk;
import jakarta.inject.Inject;
import io.quarkus.security.identity.SecurityIdentity;
import org.jboss.logging.Logger;

@Path("/talk")
public class TalkResource {

    private static final Logger LOG = Logger.getLogger(TalkResource.class);

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance detail(Talk talk,
                                             com.scanales.eventflow.model.Event event,
                                             java.util.List<Talk> occurrences,
                                             boolean inSchedule);
    }

    @Inject
    EventService eventService;

    @Inject
    SecurityIdentity identity;

    @Inject
    UserScheduleService userSchedule;

    @Inject
    UsageMetricsService metrics;

    @GET
    @Path("{id}")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public Response detail(@PathParam("id") String id,
            @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
            @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
        String ua = headers.getHeaderString("User-Agent");
        String sessionId = context.session() != null ? context.session().id() : null;
        metrics.recordPageView("/talk", sessionId, ua);
        try {
            Talk talk = eventService.findTalk(id);
            if (talk == null) {
                LOG.warnf("Talk %s not found", id);
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            var event = eventService.findEventByTalk(id);
            var occurrences = eventService.findTalkOccurrences(id);
            metrics.recordTalkView(id, sessionId, ua);
            if (talk.getLocation() != null) {
                metrics.recordStageVisit(talk.getLocation(), event != null ? event.getTimezone() : null, sessionId, ua);
            }

            java.util.List<String> missing = new java.util.ArrayList<>();
            if (talk.getLocation() == null) missing.add("location");
            if (talk.getName() == null) missing.add("name");
            if (talk.getStartTime() == null) missing.add("startTime");
            if (event == null) missing.add("event");
            if (talk.getSpeakers() == null || talk.getSpeakers().isEmpty()) missing.add("speaker");
            if (!missing.isEmpty()) {
                LOG.warnf("Talk %s missing data: %s", id, String.join(", ", missing));
            }

            boolean inSchedule = false;
            if (identity != null && !identity.isAnonymous()) {
                String email = identity.getAttribute("email");
                if (email == null) {
                    var principal = identity.getPrincipal();
                    email = principal != null ? principal.getName() : null;
                }
                if (email != null) {
                    inSchedule = userSchedule.getTalksForUser(email).contains(id);
                }
            }
            return Response.ok(Templates.detail(talk, event, occurrences, inSchedule)).build();
        } catch (Exception e) {
            LOG.errorf(e, "Error rendering talk %s", id);
            return Response.serverError().build();
        }
    }
}
