package com.scanales.eventflow.public_;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UsageMetricsService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Path("/")
public class HomeResource {

  @Inject EventService eventService;

  @Inject UsageMetricsService metrics;

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance home(
        java.util.List<com.scanales.eventflow.model.Event> upcoming,
        java.util.List<com.scanales.eventflow.model.Event> past,
        LocalDate today,
        String version,
        Map<String, String> stats,
        Map<String, String> links);
  }

  @GET
  @PermitAll
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance home(
      @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers,
      @jakarta.ws.rs.core.Context io.vertx.ext.web.RoutingContext context) {
    metrics.recordPageView("/", headers, context);
    var allEvents = eventService.listEvents();
    List<Event> upcoming =
        allEvents.stream()
            .filter(
                e -> {
                  ZonedDateTime end = e.getEndDateTime();
                  return end == null || !end.isBefore(ZonedDateTime.now(e.getZoneId()));
                })
            .sorted(
                Comparator.comparing(
                    Event::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    List<Event> past =
        allEvents.stream()
            .filter(
                e -> {
                  ZonedDateTime end = e.getEndDateTime();
                  return end != null && end.isBefore(ZonedDateTime.now(e.getZoneId()));
                })
            .sorted(
                Comparator.comparing(
                        Event::getEndDateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                    .reversed())
            .toList();
    var today = LocalDate.now();
    var stats =
        Map.of(
            "status",
            "En desarrollo",
            "lastUpdated",
            today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    var links =
        Map.of(
            "releasesUrl", "https://github.com/scanalesespinoza/eventflow/releases",
            "issuesUrl", "https://github.com/scanalesespinoza/eventflow/issues",
            "donateUrl", "https://ko-fi.com/sergiocanales");
    return Templates.home(upcoming, past, today, "2.2.0", stats, links);
  }

  @GET
  @Path("/events")
  @PermitAll
  public Response legacyEvents() {
    return Response.status(Response.Status.MOVED_PERMANENTLY).location(URI.create("/")).build();
  }
}
