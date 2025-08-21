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
      @jakarta.ws.rs.QueryParam("qr") String qr,
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
      boolean fromQr = qr != null;
      if (context.session() != null) {
        String pending = context.session().get("qr-talk");
        if (canonicalTalkId.equals(pending)) {
          fromQr = true;
          context.session().remove("qr-talk");
        }
      }
      if (fromQr && (identity == null || identity.isAnonymous())) {
        if (context.session() != null) {
          context.session().put("qr-talk", canonicalTalkId);
        }
        String target = "/event/" + eventId + "/talk/" + talkId;
        String enc = java.net.URLEncoder.encode(target, java.nio.charset.StandardCharsets.UTF_8);
        return Response.seeOther(java.net.URI.create("/login?redirect=" + enc)).build();
      }

      boolean inSchedule = false;
      UserScheduleService.TalkDetails details = null;
      boolean canEdit = false;
      if (identity != null && !identity.isAnonymous()) {
        String email = identity.getAttribute("email");
        if (email == null) {
          var principal = identity.getPrincipal();
          email = principal != null ? principal.getName() : null;
        }
        if (email != null) {
          inSchedule = userSchedule.getTalksForUser(email).contains(canonicalTalkId);
          if (fromQr) {
            if (!inSchedule) {
              boolean added = userSchedule.addTalkForUser(email, canonicalTalkId);
              if (added) {
                metrics.recordTalkRegister(canonicalTalkId, talk.getSpeakers(), ua);
              }
              inSchedule = userSchedule.getTalksForUser(email).contains(canonicalTalkId);
            }
            userSchedule.updateTalk(email, canonicalTalkId, true, null, null, null);
            return Response.seeOther(java.net.URI.create("/profile")).build();
          }
          details = userSchedule.getTalkDetailsForUser(email).get(canonicalTalkId);
          if (details != null && details.ratedAt != null) {
            canEdit =
                details
                    .ratedAt
                    .plus(java.time.Duration.ofHours(24))
                    .isAfter(java.time.Instant.now());
          } else {
            canEdit = true;
          }
        }
      }
      return Response.ok(
              TalkResource.Templates.detail(
                  talk, event, occurrences, inSchedule, details, fromQr, canEdit))
          .build();
    } catch (Exception e) {
      LOG.errorf(e, "Error rendering talk %s", talkId);
      return Response.serverError().build();
    }
  }
}
