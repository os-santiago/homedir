package com.scanales.eventflow.public_;

import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UserScheduleService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@Path("/event")
public class EventTalkResource {

  private static final Logger LOG = Logger.getLogger(EventTalkResource.class);

  @Inject EventService eventService;
  @Inject SecurityIdentity identity;
  @Inject UserScheduleService userSchedule;
  @Inject UsageMetricsService metrics;

  private String canonicalize(String rawId) {
    int talkIdx = rawId.indexOf("-talk-");
    if (talkIdx >= 0) {
      int next = rawId.indexOf('-', talkIdx + 6);
      return next >= 0 ? rawId.substring(0, next) : rawId;
    }
    int idx = rawId.indexOf('-');
    return idx >= 0 ? rawId.substring(0, idx) : rawId;
  }

  @GET
  @Path("{eventId}/talk/{talkId}")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public Response detailWithEvent(
      @PathParam("eventId") String eventId,
      @PathParam("talkId") String talkId,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    String ua = headers.getHeaderString("User-Agent");
    String sessionId = context.session() != null ? context.session().id() : null;
    metrics.recordPageView("/event/" + eventId + "/talk", sessionId, ua);
    try {
      String canonicalTalkId = canonicalize(talkId);
      Talk talk = eventService.findTalk(eventId, canonicalTalkId);
      if (talk == null) {
        LOG.warnf("Talk %s not found", talkId);
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      var event = eventService.getEvent(eventId);
      var occurrences = eventService.findTalkOccurrences(eventId, canonicalTalkId);
      metrics.recordTalkView(canonicalTalkId, sessionId, ua);
      if (talk.getLocation() != null) {
        metrics.recordStageVisit(
            talk.getLocation(), event != null ? event.getTimezone() : null, sessionId, ua);
      }
      boolean inSchedule = false;
      if (identity != null && !identity.isAnonymous()) {
        String email = identity.getAttribute("email");
        if (email == null) {
          var principal = identity.getPrincipal();
          email = principal != null ? principal.getName() : null;
        }
        if (email != null) {
          inSchedule = userSchedule.getTalksForUser(email).contains(canonicalTalkId);
        }
      }
      return Response.ok(TalkResource.Templates.detail(talk, event, occurrences, inSchedule))
          .build();
    } catch (Exception e) {
      LOG.errorf(e, "Error rendering talk %s", talkId);
      return Response.serverError().build();
    }
  }
}
