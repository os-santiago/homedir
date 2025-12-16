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
import com.scanales.eventflow.model.QuestClass;
import com.scanales.eventflow.model.QuestProfile;
import com.scanales.eventflow.service.QuestService;
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
        String userInitial,

        java.util.List<ClassOption> classOptions,
        QuestProfile questProfile);
  }

  /** Display option for class selection. */
  public record ClassOption(String value, String displayName, String emoji, String description, boolean checked) {
  }

  /** Talks grouped by day within an event. */
  public record DayGroup(int day, java.util.List<Talk> talks) {
  }

  public record EventGroup(
      com.scanales.eventflow.model.Event event,
      java.util.List<DayGroup> days,
      java.util.List<com.scanales.eventflow.model.Speaker> speakers) {
  }

  @Inject
  EventService eventService;
  @Inject
  UserScheduleService userSchedule;
  @Inject
  UserProfileService userProfiles;
  @Inject
  UsageMetricsService metrics;
  @Inject
  SecurityIdentity identity;
  @Inject
  QuestService questService;

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance profile(
      @jakarta.ws.rs.QueryParam("githubLinked") boolean githubLinked,
      @jakarta.ws.rs.QueryParam("githubError") String githubError,
      @jakarta.ws.rs.QueryParam("linkGithub") boolean linkGithub) {
    String email = getEmail();
    String name = getClaim("name");
    if (name == null) {
      name = email;
    }
    String givenName = getClaim("given_name");
    String familyName = getClaim("family_name");
    String sub = getClaim("sub");
    if (sub == null) {
      sub = email;
    }

    var groups = getEventGroupsForUser(email);
    var info = userSchedule.getTalkDetailsForUser(email);
    var summary = userSchedule.getSummaryForUser(email);
    var userProfile = userProfiles.upsert(email, name, email);

    com.scanales.eventflow.model.QuestClass currentQc = userProfile.getQuestClass();

    java.util.List<ClassOption> classOptions = java.util.Arrays.stream(QuestClass.values())
        .map(qc -> new ClassOption(
            qc.name(),
            qc.getDisplayName(),
            qc.getEmoji(),
            qc.getDescription(),
            qc == currentQc))
        .toList();

    // Fetch Gamification Profile
    QuestProfile questProfile = questService.getProfile(email);

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
        userProfile.getGithub(),
        githubLinked,
        githubError,
        linkGithub,
        true,
        name,
        name.substring(0, 1).toUpperCase(),
        classOptions,
        questProfile);
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

  @POST
  @Path("update-class")
  @Authenticated
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response updateClass(
      @jakarta.ws.rs.FormParam("questClass") String questClass,
      @jakarta.ws.rs.FormParam("redirect") String redirect) {
    String userId = getEmail();
    com.scanales.eventflow.model.QuestClass qc = com.scanales.eventflow.model.QuestClass.fromValue(questClass);
    userProfiles.updateQuestClass(userId, qc);

    if (redirect != null && !redirect.isBlank()) {
      return Response.seeOther(java.net.URI.create(redirect)).build();
    }
    return Response.seeOther(java.net.URI.create("/private/profile")).build();
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

  private java.util.List<EventGroup> getEventGroupsForUser(String email) {
    java.util.Set<String> talkIds = userSchedule.getTalksForUser(email);
    java.util.Map<com.scanales.eventflow.model.Event, java.util.List<com.scanales.eventflow.model.Talk>> talksByEvent = new java.util.HashMap<>();

    for (String id : talkIds) {
      com.scanales.eventflow.model.TalkInfo info = eventService.findTalkInfo(id);
      if (info != null) {
        talksByEvent.computeIfAbsent(info.event(), k -> new java.util.ArrayList<>()).add(info.talk());
      }
    }

    java.util.List<EventGroup> groups = new java.util.ArrayList<>();
    for (java.util.Map.Entry<com.scanales.eventflow.model.Event, java.util.List<com.scanales.eventflow.model.Talk>> entry : talksByEvent
        .entrySet()) {
      com.scanales.eventflow.model.Event event = entry.getKey();
      java.util.List<com.scanales.eventflow.model.Talk> talks = entry.getValue();

      // Group by day
      java.util.Map<Integer, java.util.List<com.scanales.eventflow.model.Talk>> talksByDay = talks.stream()
          .collect(java.util.stream.Collectors.groupingBy(com.scanales.eventflow.model.Talk::getDay));

      java.util.List<DayGroup> dayGroups = talksByDay.entrySet().stream()
          .sorted(java.util.Map.Entry.comparingByKey())
          .map(e -> {
            e.getValue().sort(java.util.Comparator.comparing(com.scanales.eventflow.model.Talk::getStartTime));
            return new DayGroup(e.getKey(), e.getValue());
          })
          .toList();

      java.util.List<com.scanales.eventflow.model.Speaker> speakers = talks.stream()
          .flatMap(t -> t.getSpeakers().stream())
          .distinct()
          .toList();

      groups.add(new EventGroup(event, dayGroups, speakers));
    }
    return groups;
  }
}
