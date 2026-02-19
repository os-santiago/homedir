package com.scanales.eventflow.private_;

import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.model.TalkInfo;
import com.scanales.eventflow.community.CommunityBoardGroup;
import com.scanales.eventflow.community.CommunityBoardService;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UserProfileService;
import com.scanales.eventflow.service.UserScheduleService;
import com.scanales.eventflow.service.UserScheduleService.TalkDetails;
import com.scanales.eventflow.security.RedirectSanitizer;
import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import com.scanales.eventflow.model.QuestClass;
import com.scanales.eventflow.model.QuestProfile;
import com.scanales.eventflow.service.QuestService;
import com.scanales.eventflow.config.AppMessages;
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
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

@Path("/private/profile")
public class ProfileResource {

  @CheckedTemplate(requireTypeSafeExpressions = false)
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
        com.scanales.eventflow.model.UserProfile.DiscordAccount discord,
        boolean githubLinked,
        boolean discordLinked,
        boolean discordUnlinked,
        String githubError,
        String discordError,
        boolean githubRequired,
        boolean userAuthenticated,
        String userName,
        String userInitial,

        java.util.List<ClassOption> classOptions,
        QuestProfile questProfile,
        String currentLanguage,
        AppMessages i18n,
        String ogTitle,
        String ogDescription);
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
  CommunityBoardService boardService;
  @Inject
  UsageMetricsService metrics;
  @Inject
  SecurityIdentity identity;
  @Inject
  QuestService questService;
  @Inject
  AppMessages messages;

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance profile(
      @jakarta.ws.rs.QueryParam("githubLinked") boolean githubLinked,
      @jakarta.ws.rs.QueryParam("discordLinked") boolean discordLinked,
      @jakarta.ws.rs.QueryParam("discordUnlinked") boolean discordUnlinked,
      @jakarta.ws.rs.QueryParam("githubError") String githubError,
      @jakarta.ws.rs.QueryParam("discordError") String discordError,
      @jakarta.ws.rs.QueryParam("linkGithub") boolean linkGithub,
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie) {
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

    // Locale Resolution
    String lang = "en";
    if (localeCookie != null && !localeCookie.isBlank()) {
      lang = localeCookie;
    } else {
      // Fallback to profile preference if available
      java.util.Optional<com.scanales.eventflow.model.UserProfile> p = userProfiles.find(email);
      if (p.isPresent() && p.get().getPreferredLocale() != null) {
        lang = p.get().getPreferredLocale();
      }
    }
    final String finalLang = lang;

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

    // Viral Feature: Social Tags
    String ogTitle = (name != null ? name : "Developer") + "'s Homedir";
    String questClass = (userProfile != null && userProfile.getQuestClass() != null)
        ? userProfile.getQuestClass().toString()
        : "Novice";
    int xp = (userProfile != null) ? userProfile.getCurrentXp() : 0;
    String ogDescription = "Level " + (xp / 1000) + " " + questClass + ". Current XP: " + xp
        + ". Solving real engineering problems.";

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
        userProfile.getDiscord(),
        githubLinked,
        discordLinked,
        discordUnlinked,
        githubError,
        discordError,
        linkGithub,
        true,
        name,
        name.substring(0, 1).toUpperCase(),
        classOptions,
        questProfile,
        finalLang,
        messages,
        ogTitle,
        ogDescription)
        .setAttribute("locale", java.util.Locale.forLanguageTag(finalLang));
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

  @POST
  @Path("update-locale")
  @Authenticated
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response updateLocale(
      @jakarta.ws.rs.FormParam("locale") String locale,
      @jakarta.ws.rs.FormParam("redirect") String redirect) {
    String userId = getEmail();

    // Validate locale simple check
    if (locale == null || (!locale.equals("en") && !locale.equals("es"))) {
      locale = "en"; // Default fallback
    }

    // 1. Update user profile preference
    userProfiles.updateLocale(userId, locale);

    // 2. Set Cookie
    jakarta.ws.rs.core.NewCookie localeCookie = new jakarta.ws.rs.core.NewCookie.Builder("QP_LOCALE")
        .value(locale)
        .path("/")
        .maxAge(60 * 60 * 24 * 365) // 1 year
        .build();

    String target = (redirect != null && !redirect.isBlank()) ? redirect : "/private/profile";
    return Response.seeOther(java.net.URI.create(target))
        .cookie(localeCookie)
        .build();
  }

  @POST
  @Path("link-discord")
  @Authenticated
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response linkDiscord(
      @jakarta.ws.rs.FormParam("discordId") String discordId,
      @jakarta.ws.rs.FormParam("redirect") String redirect) {
    String normalizedDiscordId = normalizeId(discordId);
    String target = RedirectSanitizer.sanitizeInternalRedirect(redirect, "/private/profile");
    if (normalizedDiscordId == null) {
      return redirectWithStatus(target, "discordError", "invalid_member");
    }

    var memberOpt = boardService.findMember(CommunityBoardGroup.DISCORD_USERS, normalizedDiscordId);
    if (memberOpt.isEmpty()) {
      return redirectWithStatus(target, "discordError", "invalid_member");
    }

    String userId = getEmail();
    var existingClaim = userProfiles.findByDiscordId(normalizedDiscordId);
    if (existingClaim.isPresent()
        && existingClaim.get().getUserId() != null
        && !existingClaim.get().getUserId().equalsIgnoreCase(userId)) {
      return redirectWithStatus(target, "discordError", "already_claimed");
    }

    String name = getClaim("name");
    if (name == null || name.isBlank()) {
      name = userId;
    }
    var member = memberOpt.get();
    String profileUrl = "https://discord.com/users/" + URLEncoder.encode(normalizedDiscordId, StandardCharsets.UTF_8);
    userProfiles.linkDiscord(
        userId,
        name,
        userId,
        new com.scanales.eventflow.model.UserProfile.DiscordAccount(
            normalizedDiscordId, member.handle(), profileUrl, member.avatarUrl(), Instant.now()));
    boardService.requestRefresh("discord-claim");
    return redirectWithStatus(target, "discordLinked", "1");
  }

  @POST
  @Path("unlink-discord")
  @Authenticated
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response unlinkDiscord(@jakarta.ws.rs.FormParam("redirect") String redirect) {
    String userId = getEmail();
    String target = RedirectSanitizer.sanitizeInternalRedirect(redirect, "/private/profile");
    userProfiles.unlinkDiscord(userId);
    boardService.requestRefresh("discord-unlink");
    return redirectWithStatus(target, "discordUnlinked", "1");
  }

  private Response redirectWithStatus(String target, String key, String value) {
    String separator = target.contains("?") ? "&" : "?";
    return Response.seeOther(URI.create(target + separator + key + "=" + value)).build();
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

  private String normalizeId(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim().toLowerCase();
    return normalized.isBlank() ? null : normalized;
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
