package com.scanales.eventflow.public_;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UsageMetricsService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Path("/eventos")
public class EventsDirectoryResource {

  @Inject
  EventService eventService;

  @Inject
  UsageMetricsService metrics;

  @Inject
  SecurityIdentity identity;

  @Inject
  com.scanales.eventflow.service.UserSessionService sessionService;

  @ConfigProperty(name = "homedir.ui.v2.enabled", defaultValue = "true")
  boolean uiV2Enabled;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance eventos(
        List<Event> upcoming, List<Event> past, LocalDate today, Map<String, String> stats);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance eventos(
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/eventos", headers, context);
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
    TemplateInstance template = Templates.eventos(upcoming, past, today, stats);
    if (uiV2Enabled) {
      return withLayoutData(template, "eventos");
    }
    // TODO: definir template de fallback si en el futuro se desea una versión
    // mínima
    return withLayoutData(template, "eventos");
  }

  private TemplateInstance withLayoutData(TemplateInstance templateInstance, String activePage) {
    boolean authenticated = identity != null && !identity.isAnonymous();
    String userName = authenticated ? identity.getPrincipal().getName() : null;
    return templateInstance
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
}
