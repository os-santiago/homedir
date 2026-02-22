package com.scanales.eventflow.public_;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.GamificationService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.util.AdminUtils;
import com.scanales.eventflow.util.TemplateLocaleUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Path("/eventos")
public class EventsDirectoryResource {

  @Inject
  EventService eventService;

  @Inject
  UsageMetricsService metrics;

  @Inject
  SecurityIdentity identity;

  @Inject
  GamificationService gamificationService;

  @Inject
  com.scanales.eventflow.service.UserSessionService sessionService;

  @ConfigProperty(name = "homedir.ui.v2.enabled", defaultValue = "true")
  boolean uiV2Enabled;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance eventos(
        List<Event> upcoming,
        List<Event> past,
        LocalDate today,
        Map<String, String> stats,
        Map<String, List<String>> topTracksByEvent,
        Map<String, List<String>> recommendedSessionsByEvent);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance eventos(
      @jakarta.ws.rs.CookieParam("QP_LOCALE") String localeCookie,
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/eventos", headers, context);
    currentUserId()
        .ifPresent(
            userId -> {
              gamificationService.award(userId, GamificationActivity.EVENT_DIRECTORY_VIEW);
              gamificationService.award(
                  userId, GamificationActivity.WARRIOR_EVENTS_EXPLORATION, "events");
            });
    var all = eventService.listEvents();
    LocalDate today = LocalDate.now();
    List<Event> upcoming = all.stream()
        .filter(
            e -> {
              ZonedDateTime end = e.getEndDateTime();
              return end == null || !end.toLocalDate().isBefore(today);
            })
        .sorted(
            Comparator.comparing(
                Event::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
    List<Event> past = all.stream()
        .filter(
            e -> {
              ZonedDateTime end = e.getEndDateTime();
              return end != null && end.toLocalDate().isBefore(today);
            })
        .sorted(
            Comparator.comparing(
                Event::getEndDateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed())
        .toList();
    var stats = Map.of(
        "upcoming", Integer.toString(upcoming.size()),
        "past", Integer.toString(past.size()));
    Map<String, List<String>> topTracksByEvent = new LinkedHashMap<>();
    Map<String, List<String>> recommendedSessionsByEvent = new LinkedHashMap<>();
    for (Event event : upcoming) {
      topTracksByEvent.put(event.getId(), topTracks(event));
      recommendedSessionsByEvent.put(event.getId(), recommendedSessions(event));
    }
    TemplateInstance template =
        Templates.eventos(
            upcoming, past, today, stats, Map.copyOf(topTracksByEvent), Map.copyOf(recommendedSessionsByEvent));
    if (uiV2Enabled) {
      return withLayoutData(template, "eventos", localeCookie);
    }
    // TODO: definir template de fallback si en el futuro se desea una versión
    // mínima
    return withLayoutData(template, "eventos", localeCookie);
  }

  private TemplateInstance withLayoutData(
      TemplateInstance templateInstance, String activePage, String localeCookie) {
    boolean authenticated = identity != null && !identity.isAnonymous();
    String userName = authenticated ? identity.getPrincipal().getName() : null;
    return TemplateLocaleUtil.apply(templateInstance, localeCookie)
        .data("activePage", activePage)
        .data("userAuthenticated", authenticated)
        .data("userName", userName)
        .data("userSession", sessionService.getCurrentSession())
        .data("userInitial", initialFrom(userName));
  }

  private String initialFrom(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.substring(0, 1).toUpperCase();
  }

  private java.util.Optional<String> currentUserId() {
    if (identity == null || identity.isAnonymous()) {
      return java.util.Optional.empty();
    }
    String email = AdminUtils.getClaim(identity, "email");
    if (email != null && !email.isBlank()) {
      return java.util.Optional.of(email.toLowerCase(Locale.ROOT));
    }
    String principal = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
    if (principal != null && !principal.isBlank()) {
      return java.util.Optional.of(principal.toLowerCase(Locale.ROOT));
    }
    return java.util.Optional.empty();
  }

  private List<String> topTracks(Event event) {
    List<Talk> talks = activeTalks(event);
    if (talks.isEmpty()) {
      return List.of();
    }
    Map<String, Integer> scoreByTrack = new LinkedHashMap<>();
    for (Talk talk : talks) {
      String track = inferTrack(talk);
      scoreByTrack.merge(track, 1, Integer::sum);
    }
    return scoreByTrack.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .map(Map.Entry::getKey)
        .limit(3)
        .toList();
  }

  private List<String> recommendedSessions(Event event) {
    List<Talk> talks = activeTalks(event);
    if (talks.isEmpty()) {
      return List.of();
    }
    return talks.stream()
        .sorted(
            Comparator.comparingInt(Talk::getDay)
                .thenComparing(Talk::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(Talk::getName)
        .filter(name -> name != null && !name.isBlank())
        .limit(3)
        .toList();
  }

  private List<Talk> activeTalks(Event event) {
    if (event == null || event.getAgenda() == null || event.getAgenda().isEmpty()) {
      return List.of();
    }
    List<Talk> talks = new ArrayList<>();
    for (Talk talk : event.getAgenda()) {
      if (talk == null || talk.isBreak()) {
        continue;
      }
      talks.add(talk);
    }
    return talks;
  }

  private String inferTrack(Talk talk) {
    if (talk == null) {
      return "Delivery";
    }
    String text =
        ((talk.getName() == null ? "" : talk.getName()) + " " + (talk.getDescription() == null ? "" : talk.getDescription()))
            .toLowerCase(Locale.ROOT);
    if (text.contains("platform") || text.contains("developer platform") || text.contains("devex")) {
      return "Platform Engineering";
    }
    if (text.contains("security") || text.contains("devsecops") || text.contains("supply chain")) {
      return "Security";
    }
    if (text.contains("sre") || text.contains("observability") || text.contains("incident")) {
      return "SRE & Observability";
    }
    if (text.contains("kubernetes") || text.contains("cloud") || text.contains("cloud-native")) {
      return "Cloud Native";
    }
    if (text.contains("ai") || text.contains("llm") || text.contains("agent")) {
      return "AI for Engineering";
    }
    return "Delivery";
  }
}
