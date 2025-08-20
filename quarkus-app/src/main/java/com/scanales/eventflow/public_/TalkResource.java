package com.scanales.eventflow.public_;

import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UserScheduleService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
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

@Path("")
public class TalkResource {

  private static final Logger LOG = Logger.getLogger(TalkResource.class);

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance detail(
        Talk talk,
        com.scanales.eventflow.model.Event event,
        java.util.List<Talk> occurrences,
        boolean inSchedule,
        com.scanales.eventflow.service.UserScheduleService.TalkDetails details,
        boolean fromQr,
        boolean canEdit);
  }

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
  @Path("/talk/{id}")
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public Response detail(
      @PathParam("id") String id,
      @jakarta.ws.rs.QueryParam("qr") String qr,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    String ua = headers.getHeaderString("User-Agent");
    String sessionId = context.session() != null ? context.session().id() : null;
    metrics.recordPageView("/talk", sessionId, ua);
    try {
      String canonicalId = canonicalize(id);
      Talk talk = eventService.findTalk(canonicalId);
      if (talk == null) {
        LOG.warnf("Talk %s not found", id);
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      var event = eventService.findEventByTalk(canonicalId);
      var occurrences = eventService.findTalkOccurrences(canonicalId);
      metrics.recordTalkView(canonicalId, sessionId, ua);
      if (talk.getLocation() != null) {
        metrics.recordStageVisit(
            talk.getLocation(), event != null ? event.getTimezone() : null, sessionId, ua);
      }

      java.util.List<String> missing = new java.util.ArrayList<>();
      if (talk.getLocation() == null) missing.add("location");
      if (talk.getName() == null) missing.add("name");
      if (talk.getStartTime() == null) missing.add("startTime");
      if (event == null) missing.add("event");
      if (talk.getSpeakers() == null || talk.getSpeakers().isEmpty()) missing.add("speaker");
      if (!missing.isEmpty()) {
        LOG.warnf("Talk %s missing data: %s", canonicalId, String.join(", ", missing));
      }

      boolean fromQr = qr != null;
      if (fromQr && (identity == null || identity.isAnonymous())) {
        String target = "/talk/" + id + "?qr=1";
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
          inSchedule = userSchedule.getTalksForUser(email).contains(canonicalId);
          if (fromQr && !inSchedule) {
            boolean added = userSchedule.addTalkForUser(email, canonicalId);
            if (added) {
              metrics.recordTalkRegister(canonicalId, talk.getSpeakers(), ua);
            }
            inSchedule = userSchedule.getTalksForUser(email).contains(canonicalId);
          }
          details = userSchedule.getTalkDetailsForUser(email).get(canonicalId);
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
              Templates.detail(talk, event, occurrences, inSchedule, details, fromQr, canEdit))
          .build();
    } catch (Exception e) {
      LOG.errorf(e, "Error rendering talk %s", id);
      return Response.serverError().build();
    }
  }
}
