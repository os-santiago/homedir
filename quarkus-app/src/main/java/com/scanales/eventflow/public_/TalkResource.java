package com.scanales.eventflow.public_;

import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UserScheduleService;
import com.scanales.eventflow.util.AdminUtils;
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
import java.util.Locale;
import java.util.Optional;
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
        boolean inSchedule);
  }

  @Inject EventService eventService;

  @Inject SecurityIdentity identity;

  @Inject UserScheduleService userSchedule;

  @Inject UsageMetricsService metrics;

  @Inject GamificationService gamificationService;

  private String legacyCanonicalize(String rawId) {
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
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    String ua = headers.getHeaderString("User-Agent");
    String sessionId = context.session() != null ? context.session().id() : null;
    metrics.recordPageView("/talk", sessionId, ua);
    try {
      String canonicalId = id;
      Talk talk = eventService.findTalk(canonicalId);
      if (talk == null) {
        String legacyId = legacyCanonicalize(id);
        if (!legacyId.equals(id)) {
          canonicalId = legacyId;
          talk = eventService.findTalk(canonicalId);
        }
      }
      if (talk == null) {
        LOG.warnf("Talk %s not found", id);
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      final String resolvedTalkId = canonicalId;
      currentUserId()
          .ifPresent(
              userId ->
                  gamificationService.award(userId, GamificationActivity.TALK_VIEW, resolvedTalkId));
      var event = eventService.findEventByTalk(resolvedTalkId);
      var occurrences = eventService.findTalkOccurrences(resolvedTalkId);
      metrics.recordTalkView(resolvedTalkId, sessionId, ua);
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

      boolean inSchedule = false;
      if (identity != null && !identity.isAnonymous()) {
        String email = identity.getAttribute("email");
        if (email == null) {
          var principal = identity.getPrincipal();
          email = principal != null ? principal.getName() : null;
        }
        if (email != null) {
          inSchedule = userSchedule.getTalksForUser(email).contains(resolvedTalkId);
        }
      }
      return Response.ok(Templates.detail(talk, event, occurrences, inSchedule)).build();
    } catch (Exception e) {
      LOG.errorf(e, "Error rendering talk %s", id);
      return Response.serverError().build();
    }
  }

  private Optional<String> currentUserId() {
    if (identity == null || identity.isAnonymous()) {
      return Optional.empty();
    }
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return Optional.of(email.toLowerCase(Locale.ROOT));
    }
    String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    if (principal == null || principal.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(principal.toLowerCase(Locale.ROOT));
  }
}
