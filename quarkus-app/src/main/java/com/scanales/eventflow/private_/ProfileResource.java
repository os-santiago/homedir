package com.scanales.eventflow.private_;

import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.model.TalkInfo;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UserProfileService;
import com.scanales.eventflow.service.UserScheduleService;
import com.scanales.eventflow.service.UserScheduleService.TalkDetails;
import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.jboss.logging.Logger;

@Path("/private/profile")
public class ProfileResource {

  private static final Logger LOG = Logger.getLogger(ProfileResource.class);

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance profile(
        String name,
        String givenName,
        String familyName,
        String email,
        String sub,
        java.util.List<EventGroup> groups,
        java.util.Map<String, TalkDetails> info,
        int totalTalks,
        long attendedTalks,
        long ratedTalks,
        com.scanales.eventflow.model.UserProfile.GithubAccount github,
        boolean githubLinked,
        String githubError,
        boolean githubRequired,
        boolean userAuthenticated,
        String userName,
        String userInitial);
  }

  /** Talks grouped by day within an event. */
  public record DayGroup(int day, java.util.List<Talk> talks) {
  }

  /** Talks grouped by event. */
  public record EventGroup(
      com.scanales.eventflow.model.Event event,
      java.util.List<DayGroup> days,
      java.util.List<com.scanales.eventflow.model.Speaker> speakers) {
  }

  @Inject
  SecurityIdentity identity;

  @Inject
  EventService eventService;

  @Inject
  UserScheduleService userSchedule;

  @Inject
  UsageMetricsService metrics;

  @Inject
  UserProfileService userProfiles;

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance profile(
      @jakarta.ws.rs.QueryParam("githubLinked") boolean githubLinked,
      @jakarta.ws.rs.QueryParam("githubError") String githubError,
      @jakarta.ws.rs.QueryParam("linkGithub") boolean linkGithub) {
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

    var info = userSchedule.getTalkDetailsForUser(email);
    var talkIds = info.keySet();
    java.util.List<TalkInfo> entries = talkIds.stream()
        .map(eventService::findTalkInfo)
        .filter(java.util.Objects::nonNull)
        .toList();

    // Group talks by event and day, and collect speakers per event
    java.util.Map<com.scanales.eventflow.model.Event, java.util.Map<Integer, java.util.List<Talk>>> grouped = new java.util.LinkedHashMap<>();
    java.util.Map<com.scanales.eventflow.model.Event, java.util.Map<String, com.scanales.eventflow.model.Speaker>> speakersByEvent = new java.util.LinkedHashMap<>();
    for (TalkInfo te : entries) {
      grouped
          .computeIfAbsent(te.event(), k -> new java.util.TreeMap<>())
          .computeIfAbsent(te.talk().getDay(), k -> new java.util.ArrayList<>())
          .add(te.talk());
      java.util.Map<String, com.scanales.eventflow.model.Speaker> eventSpeakers = speakersByEvent.computeIfAbsent(
          te.event(),
          k -> new java.util.LinkedHashMap<String, com.scanales.eventflow.model.Speaker>());
      te.talk().getSpeakers().stream()
          .filter(s -> s.getId() != null && !s.getId().isBlank())
          .forEach(s -> eventSpeakers.putIfAbsent(s.getId(), s));
    }
    java.util.List<EventGroup> groups = grouped.entrySet().stream()
        .map(
            ev -> new EventGroup(
                ev.getKey(),
                ev.getValue().entrySet().stream()
                    .map(d -> new DayGroup(d.getKey(), d.getValue()))
                    .toList(),
                new java.util.ArrayList<>(
                    speakersByEvent
                        .getOrDefault(ev.getKey(), java.util.Map.of())
                        .values())))
        .toList();

    var summary = userSchedule.getSummaryForUser(email);
    return Templates.profile(
        name,
        givenName,
        familyName,
        email,
        sub,
        groups,
        info,
        summary.total(),
        summary.attended(),
        summary.rated(),
        userProfiles.upsert(email, name, email).getGithub(),
        githubLinked,
        githubError,
        linkGithub,
        true,
        name,
        name.substring(0, 1).toUpperCase());
  }

  @GET
  @Path("add/{id}")
  @Authenticated
  public Response addTalkRedirect(
      @PathParam("id") String id,
      @Context HttpHeaders headers,
      @jakarta.ws.rs.QueryParam("visited") boolean visited,
      @jakarta.ws.rs.QueryParam("attended") boolean attended) {
    String email = getEmail();
    String name = getClaim("name");
    if (name == null) {
      name = email;
    }
    boolean added = userSchedule.addTalkForUser(email, id);
    if (added) {
      var talk = eventService.findTalk(id);
      metrics.recordTalkRegister(
          id,
          talk != null ? talk.getSpeakers() : java.util.List.of(),
          headers.getHeaderString("User-Agent"),
          name,
          email);
    }
    if (visited || attended) {
      userSchedule.updateTalk(email, id, true, null, null, null);
      return Response.seeOther(java.net.URI.create("/private/profile")).build();
    }
    return Response.status(Response.Status.SEE_OTHER).header("Location", "/talk/" + id).build();
  }

  @POST
  @Path("update/{id}")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response updateTalk(@PathParam("id") String id, @Valid UpdateRequest req) {
    String email = getEmail();
    boolean ok = userSchedule.updateTalk(email, id, req.attended, req.rating, req.motivations, req.comment);
    String status = ok ? "updated" : "missing";
    return Response.ok(java.util.Map.of("status", status)).build();
  }

  @RegisterForReflection
  public record UpdateRequest(
      Boolean attended,
      @Min(1) @Max(5) Integer rating,
      java.util.Set<String> motivations,
      @jakarta.validation.constraints.Size(max = 200) String comment) {
  }

  @POST
  @Path("add/{id}")
  @Authenticated
  @Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_HTML })
  public Response addTalk(@PathParam("id") String id, @Context HttpHeaders headers) {
    String email = getEmail();
    String name = getClaim("name");
    if (name == null) {
      name = email;
    }
    boolean added = userSchedule.addTalkForUser(email, id);
    if (added) {
      var talk = eventService.findTalk(id);
      metrics.recordTalkRegister(
          id,
          talk != null ? talk.getSpeakers() : java.util.List.of(),
          headers.getHeaderString("User-Agent"),
          name,
          email);
    }
    String status = added ? "added" : "exists";
    if (acceptsJson(headers)) {
      var info = eventService.findTalkInfo(id);
      java.util.Map<String, Object> body = new java.util.HashMap<>();
      body.put("status", status);
      body.put("talkId", id);
      if (info != null) {
        body.put("eventId", info.event().getId());
      }
      return Response.ok(body).type(MediaType.APPLICATION_JSON).build();
    }
    return Response.status(Response.Status.SEE_OTHER).header("Location", "/talk/" + id).build();
  }

  @GET
  @Path("remove/{id}")
  @Authenticated
  public Response removeTalkRedirect(@PathParam("id") String id) {
    String email = getEmail();
    boolean removed = userSchedule.removeTalkForUser(email, id);
    if (removed) {
      metrics.recordTalkUnregister(id, email);
    }
    return Response.status(Response.Status.SEE_OTHER)
        .header("Location", "/private/profile")
        .build();
  }

  @POST
  @Path("remove/{id}")
  @Authenticated
  @Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_HTML })
  public Response removeTalk(@PathParam("id") String id, @Context HttpHeaders headers) {
    String email = getEmail();
    boolean removed = userSchedule.removeTalkForUser(email, id);
    if (removed) {
      metrics.recordTalkUnregister(id, email);
    }
    String status = removed ? "removed" : "missing";
    if (acceptsJson(headers)) {
      return Response.ok(java.util.Map.of("status", status, "talkId", id))
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
    return Response.status(Response.Status.SEE_OTHER)
        .header("Location", "/private/profile")
        .build();
  }

  private boolean acceptsJson(HttpHeaders headers) {
    String accept = headers.getHeaderString(HttpHeaders.ACCEPT);
    return accept != null && accept.toLowerCase().contains(MediaType.APPLICATION_JSON);
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
