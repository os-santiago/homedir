package com.scanales.eventflow.private_;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.SpeakerService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UsageMetricsService.Health;
import com.scanales.eventflow.service.UsageMetricsService.Summary;
import com.scanales.eventflow.util.AdminUtils;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Admin page for usage metrics with simple reporting and CSV export. */
@Path("/private/admin/metrics")
public class AdminMetricsResource {

  public record ConversionRow(
      String id, String name, long views, long registrations, String conversion) {
  }

  public record CtaDayRow(
      LocalDate date, long releases, long issues, long kofi, long total, boolean peak) {
  }

  public record DataHealth(String state, String css, String tooltip) {
  }

  @RegisterForReflection
  public record StatusPayload(String state, String css, String tooltip, String last, long hash) {
  }

  public record CtaData(
      long releases,
      long issues,
      long kofi,
      double avgReleases,
      double avgIssues,
      double avgKofi,
      double meanTotal,
      double stdTotal,
      int activeDays,
      List<CtaDayRow> rows,
      int peakCount) {
  }

  /** Summary row for event-level metrics. */
  public record EventSummaryRow(
      String id, String name, long eventViews, long talkViews, long registrations) {
  }

  public record MetricsData(
      long eventsViewed,
      long talksViewed,
      long talksRegistered,
      long stageVisits,
      String lastUpdate,
      String lastUpdateRel,
      long lastUpdateMillis,
      Map<String, Long> discards,
      UsageMetricsService.Config config,
      List<ConversionRow> topTalks,
      List<ConversionRow> topSpeakers,
      List<ConversionRow> topScenarios,
      String globalConversion,
      long expectedAttendees,
      boolean empty,
      String range,
      String eventId,
      int schemaVersion,
      long fileSizeBytes,
      int minViews,
      Health health,
      DataHealth dataHealth,
      String talkQ,
      String talkSort,
      String talkDir,
      String speakerQ,
      String speakerSort,
      String speakerDir,
      String scenarioQ,
      String scenarioSort,
      String scenarioDir,
      List<Event> events,
      List<Scenario> stages,
      List<Speaker> speakers,
      String stageId,
      String speakerId,
      CtaData ctas,
      String ctaQ,
      List<EventSummaryRow> eventRows) {
  }

  /** Row representing registrations for a talk. */
  public record TalkRegistrationRow(String id, String name, long registrations) {
  }

  public record EventTalks(Event event, List<TalkRegistrationRow> rows) {
  }

  /** Payload with dependent filter options. */
  public record FilterData(List<Scenario> stages, List<Speaker> speakers) {
  }

  @CheckedTemplate
  static class Templates {
    static native TemplateInstance index(MetricsData data);

    static native TemplateInstance guide();

    static native TemplateInstance talks(Event event, List<TalkRegistrationRow> rows);

    static native TemplateInstance registrations(List<EventTalks> events);

    static native TemplateInstance registrants(
        Event event,
        Talk talk,
        List<UsageMetricsService.Registrant> users,
        int page,
        int size,
        long total,
        String name,
        String email,
        boolean hasPrev,
        boolean hasNext,
        int prev,
        int next);
  }

  @Inject
  SecurityIdentity identity;

  @Inject
  UsageMetricsService metrics;

  @Inject
  EventService eventService;

  @Inject
  SpeakerService speakerService;

  @GET
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response metrics(
      @QueryParam("range") String range,
      @QueryParam("event") String eventId,
      @QueryParam("stage") String stageId,
      @QueryParam("speaker") String speakerId,
      @QueryParam("talkQ") String talkQ,
      @QueryParam("talkSort") String talkSort,
      @QueryParam("talkDir") String talkDir,
      @QueryParam("speakerQ") String speakerQ,
      @QueryParam("speakerSort") String speakerSort,
      @QueryParam("speakerDir") String speakerDir,
      @QueryParam("scenarioQ") String scenarioQ,
      @QueryParam("scenarioSort") String scenarioSort,
      @QueryParam("scenarioDir") String scenarioDir,
      @QueryParam("ctaQ") String ctaQ) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    MetricsData data = buildData(range, eventId, stageId, speakerId);
    List<ConversionRow> talks = filterRows(data.topTalks(), talkQ, talkSort, talkDir);
    List<ConversionRow> speakers = filterRows(data.topSpeakers(), speakerQ, speakerSort, speakerDir);
    List<ConversionRow> scenarios = filterRows(data.topScenarios(), scenarioQ, scenarioSort, scenarioDir);
    List<CtaDayRow> ctaRows = filterCtaRows(data.ctas().rows(), ctaQ);
    CtaData ctas = new CtaData(
        data.ctas().releases(),
        data.ctas().issues(),
        data.ctas().kofi(),
        data.ctas().avgReleases(),
        data.ctas().avgIssues(),
        data.ctas().avgKofi(),
        data.ctas().meanTotal(),
        data.ctas().stdTotal(),
        data.ctas().activeDays(),
        ctaRows,
        data.ctas().peakCount());
    data = new MetricsData(
        data.eventsViewed(),
        data.talksViewed(),
        data.talksRegistered(),
        data.stageVisits(),
        data.lastUpdate(),
        data.lastUpdateRel(),
        data.lastUpdateMillis(),
        data.discards(),
        data.config(),
        talks,
        speakers,
        scenarios,
        data.globalConversion(),
        data.expectedAttendees(),
        data.empty(),
        data.range(),
        data.eventId(),
        data.schemaVersion(),
        data.fileSizeBytes(),
        data.minViews(),
        data.health(),
        data.dataHealth(),
        talkQ,
        talkSort,
        talkDir,
        speakerQ,
        speakerSort,
        speakerDir,
        scenarioQ,
        scenarioSort,
        scenarioDir,
        data.events(),
        data.stages(),
        data.speakers(),
        data.stageId(),
        data.speakerId(),
        ctas,
        ctaQ,
        data.eventRows());
    return Response.ok(Templates.index(data)).build();
  }

  @GET
  @Path("status")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  public Response status(
      @QueryParam("range") String range,
      @QueryParam("event") String eventId,
      @QueryParam("stage") String stageId,
      @QueryParam("speaker") String speakerId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    long start = System.currentTimeMillis();
    try {
      MetricsData data = buildData(range, eventId, stageId, speakerId);
      metrics.recordRefresh(true, System.currentTimeMillis() - start);
      StatusPayload payload = new StatusPayload(
          data.dataHealth().state(),
          data.dataHealth().css(),
          data.dataHealth().tooltip(),
          data.lastUpdateRel(),
          data.lastUpdateMillis());
      return Response.ok(payload).build();
    } catch (Exception e) {
      metrics.recordRefresh(false, System.currentTimeMillis() - start);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("filters")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  public Response filters(@QueryParam("event") String eventId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    List<Scenario> stages = eventId != null && !eventId.isBlank()
        ? Optional.ofNullable(eventService.getEvent(eventId))
            .map(Event::getScenarios)
            .orElse(List.of())
        : List.of();
    List<Speaker> speakers;
    if (eventId != null && !eventId.isBlank()) {
      Event ev = eventService.getEvent(eventId);
      if (ev != null) {
        speakers = ev.getAgenda().stream()
            .flatMap(t -> t.getSpeakers().stream())
            .collect(Collectors.toMap(Speaker::getId, Function.identity(), (a, b) -> a))
            .values()
            .stream()
            .sorted(
                Comparator.comparing(
                    Speaker::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();
      } else {
        speakers = List.of();
      }
    } else {
      speakers = speakerService.listSpeakers().stream()
          .sorted(
              Comparator.comparing(
                  Speaker::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
          .toList();
    }
    return Response.ok(new FilterData(stages, speakers)).build();
  }

  @GET
  @Path("health")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return Response.ok(metrics.getHealth()).build();
  }

  @GET
  @Path("guide")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response guide() {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    return Response.ok(Templates.guide()).build();
  }

  @GET
  @Path("export")
  @Authenticated
  @Produces("text/csv")
  public Response export(
      @QueryParam("table") String table,
      @QueryParam("range") String range,
      @QueryParam("event") String eventId,
      @QueryParam("stage") String stageId,
      @QueryParam("speaker") String speakerId,
      @QueryParam("q") String query,
      @QueryParam("sort") String sort,
      @QueryParam("dir") String dir) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    String tableName = (table == null || table.isBlank()) ? "talks" : table;
    MetricsData data = buildData(range, eventId, stageId, speakerId, 0);
    StringBuilder sb = new StringBuilder();
    if ("ctas".equals(tableName)) {
      List<CtaDayRow> rows = filterCtaRows(data.ctas().rows(), query);
      sb.append("Fecha,Releases,Issues,Ko-fi,Total\n");
      for (CtaDayRow r : rows) {
        sb.append(r.date())
            .append(',')
            .append(r.releases())
            .append(',')
            .append(r.issues())
            .append(',')
            .append(r.kofi())
            .append(',')
            .append(r.total())
            .append('\n');
      }
    } else {
      List<ConversionRow> rows;
      String header;
      switch (tableName) {
        case "talks" -> {
          rows = data.topTalks();
          header = "Charla,Vistas,Registros,Conversion";
        }
        case "speakers" -> {
          rows = data.topSpeakers();
          header = "Orador,Vistas,Registros,Conversion";
        }
        case "scenarios" -> {
          rows = data.topScenarios();
          header = "Escenario,Vistas,Registros,Conversion";
        }
        default -> {
          rows = List.of();
          header = "";
        }
      }
      rows = filterRows(rows, query, sort, dir);
      if (!header.isEmpty()) {
        sb.append(header).append('\n');
        for (ConversionRow r : rows) {
          sb.append(escapeCsv(r.name()))
              .append(',')
              .append(r.views())
              .append(',')
              .append(r.registrations())
              .append(',')
              .append(r.conversion())
              .append('\n');
        }
      }
    }
    String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
    String safeRange = range == null || range.isBlank() ? "all" : range;
    return Response.ok(sb.toString())
        .header(
            "Content-Disposition",
            "attachment; filename=metrics-" + tableName + "-" + safeRange + "-" + ts + ".csv")
        .build();
  }

  @GET
  @Path("data")
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  public Response downloadData() {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    try {
      java.nio.file.Path dataDir = Paths.get(System.getProperty("homedir.data.dir", "data"));
      java.nio.file.Path file = dataDir.resolve("metrics-v2.json");
      if (!Files.exists(file)) {
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      byte[] json = Files.readAllBytes(file);
      return Response.ok(json)
          .header("Content-Disposition", "attachment; filename=metrics.json")
          .build();
    } catch (Exception e) {
      return Response.serverError().build();
    }
  }

  @POST
  @Path("reset")
  @Authenticated
  public Response resetData() {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    metrics.reset();
    return Response.seeOther(UriBuilder.fromPath("/private/admin/metrics").build()).build();
  }

  @GET
  @Path("talks")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response talksReport(@QueryParam("event") String eventId) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    if (eventId == null || eventId.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    Event event = eventService.getEvent(eventId);
    if (event == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    Map<String, List<UsageMetricsService.Registrant>> regs = metrics.getRegistrations();
    List<TalkRegistrationRow> rows = event.getAgenda().stream()
        .map(
            t -> {
              String name = t.getName() != null ? t.getName() : t.getId();
              long count = regs.getOrDefault(t.getId(), List.of()).size();
              return new TalkRegistrationRow(t.getId(), name, count);
            })
        .sorted(
            java.util.Comparator.comparingLong(TalkRegistrationRow::registrations).reversed())
        .toList();
    return Response.ok(Templates.talks(event, rows)).build();
  }

  @GET
  @Path("registrations")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response registrations() {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    Map<String, List<UsageMetricsService.Registrant>> regs = metrics.getRegistrations();
    List<Event> events = eventService.listEvents().stream().filter(this::isActive).toList();
    List<EventTalks> data = events.stream()
        .map(
            ev -> {
              List<TalkRegistrationRow> rows = ev.getAgenda().stream()
                  .map(
                      t -> new TalkRegistrationRow(
                          t.getId(),
                          t.getName() != null ? t.getName() : t.getId(),
                          regs.getOrDefault(t.getId(), List.of()).size()))
                  .sorted(
                      java.util.Comparator.comparingLong(TalkRegistrationRow::registrations)
                          .reversed())
                  .toList();
              return new EventTalks(ev, rows);
            })
        .toList();
    return Response.ok(Templates.registrations(data)).build();
  }

  @GET
  @Path("registrants")
  @Authenticated
  @Produces(MediaType.TEXT_HTML)
  public Response registrants(
      @QueryParam("talk") String talkId,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("100") int size,
      @QueryParam("name") String name,
      @QueryParam("email") String email) {
    if (!AdminUtils.isAdmin(identity)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    if (talkId == null || talkId.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    var info = eventService.findTalkInfo(talkId);
    if (info == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    List<UsageMetricsService.Registrant> users = metrics.getRegistrants(talkId);
    if (name != null && !name.isBlank()) {
      String q = name.toLowerCase();
      users = users.stream()
          .filter(u -> u.name() != null && u.name().toLowerCase().contains(q))
          .toList();
    }
    if (email != null && !email.isBlank()) {
      String q = email.toLowerCase();
      users = users.stream()
          .filter(u -> u.email() != null && u.email().toLowerCase().contains(q))
          .toList();
    }
    int pageSize = (size == 20 || size == 50) ? size : 100;
    int total = users.size();
    int maxPage = Math.max(1, (int) Math.ceil((double) total / pageSize));
    int current = Math.min(Math.max(page, 1), maxPage);
    int from = Math.min((current - 1) * pageSize, total);
    int to = Math.min(from + pageSize, total);
    List<UsageMetricsService.Registrant> pageUsers = users.subList(from, to);
    boolean hasPrev = current > 1;
    boolean hasNext = current * pageSize < total;
    int prev = hasPrev ? current - 1 : current;
    int next = hasNext ? current + 1 : current;
    return Response.ok(
        Templates.registrants(
            info.event(),
            info.talk(),
            pageUsers,
            current,
            pageSize,
            total,
            name,
            email,
            hasPrev,
            hasNext,
            prev,
            next))
        .build();
  }

  private boolean isActive(Event event) {
    if (event.getDate() == null) {
      return true;
    }
    return event.getDate().plusDays(event.getDays()).isAfter(LocalDate.now());
  }

  @ConfigProperty(name = "metrics.min-view-threshold", defaultValue = "20")
  int minViews;

  private MetricsData buildData(String range, String eventId, String stageId, String speakerId) {
    return buildData(range, eventId, stageId, speakerId, minViews);
  }

  private MetricsData buildData(
      String range, String eventId, String stageId, String speakerId, int threshold) {
    Map<String, Long> snap = metrics.snapshot();
    Summary summary = metrics.getSummary();
    Health health = metrics.getHealth();

    long eventsViewed;
    if (eventId != null && !eventId.isBlank()) {
      eventsViewed = snap.getOrDefault("event_view:" + eventId, 0L);
    } else {
      eventsViewed = sumByPrefix(snap, "event_view:");
    }

    Map<String, Stats> talkStats = buildTalkStats(snap, eventId, stageId, speakerId);
    long talksViewed = talkStats.values().stream().mapToLong(s -> s.views).sum();
    long talksRegistered = talkStats.values().stream().mapToLong(s -> s.regs).sum();

    // Use UTC dates to match the way metrics are recorded.
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate start;
    if ("today".equals(range)) {
      start = today;
    } else if ("7".equals(range)) {
      start = today.minusDays(6);
    } else if ("30".equals(range)) {
      start = today.minusDays(29);
    } else {
      start = LocalDate.MIN;
      range = "all";
    }
    Map<String, Long> stageMap = new HashMap<>();
    for (Map.Entry<String, Long> e : snap.entrySet()) {
      if (e.getKey().startsWith("stage_visit:")) {
        String[] parts = e.getKey().split(":");
        if (parts.length == 3) {
          LocalDate d = LocalDate.parse(parts[2]);
          if (!d.isBefore(start)) {
            String stId = parts[1];
            if ((eventId == null || belongsToEvent(stId, eventId))
                && (stageId == null || stageId.isBlank() || stageId.equals(stId))) {
              stageMap.merge(stId, e.getValue(), Long::sum);
            }
          }
        }
      }
    }
    long stageVisits = stageMap.values().stream().mapToLong(Long::longValue).sum();

    CtaData ctaData = buildCtaData(snap, start);

    List<ConversionRow> topTalks = topConversionRows(talkStats, 10, this::talkName, threshold);
    Map<String, Stats> speakerStats = aggregateSpeakers(talkStats);
    List<ConversionRow> topSpeakers = topConversionRows(speakerStats, 10, this::speakerName, threshold);
    Map<String, Stats> scenarioStats = aggregateScenarios(talkStats);
    List<ConversionRow> topScenarios = topConversionRows(scenarioStats, 10, this::stageName, threshold);

    List<Event> events = eventService.listEvents();
    Map<String, Stats> eventStats = aggregateEvents(talkStats);
    Map<String, Long> eventViewMap = extractMap(snap, "event_view:");
    List<EventSummaryRow> eventRows = events.stream()
        .map(
            ev -> {
              Stats s = eventStats.getOrDefault(ev.getId(), new Stats());
              long evViews = eventViewMap.getOrDefault(ev.getId(), 0L);
              return new EventSummaryRow(ev.getId(), ev.getTitle(), evViews, s.views, s.regs);
            })
        .sorted(java.util.Comparator.comparingLong(EventSummaryRow::eventViews).reversed())
        .toList();

    long last = metrics.getLastUpdatedMillis();
    String lastStr = last > 0 ? Instant.ofEpochMilli(last).toString() : "—";
    String lastRel = formatAge(last);

    String globalConv = formatConversion(talksViewed, talksRegistered);
    long expectedAttendees = talksRegistered;

    boolean empty = eventsViewed == 0
        && talksViewed == 0
        && talksRegistered == 0
        && stageVisits == 0
        && topTalks.isEmpty()
        && topSpeakers.isEmpty()
        && topScenarios.isEmpty()
        && ctaData.rows().isEmpty();
    DataHealth dataHealth = computeDataHealth(empty, System.currentTimeMillis() - last, range);

    List<Scenario> stages = eventId != null
        ? Optional.ofNullable(eventService.getEvent(eventId))
            .map(Event::getScenarios)
            .orElse(List.of())
        : List.of();
    List<Speaker> speakers = speakerService.listSpeakers();

    return new MetricsData(
        eventsViewed,
        talksViewed,
        talksRegistered,
        stageVisits,
        lastStr,
        lastRel,
        last,
        summary.discarded(),
        metrics.getConfig(),
        topTalks,
        topSpeakers,
        topScenarios,
        globalConv,
        expectedAttendees,
        empty,
        range,
        eventId,
        metrics.getSchemaVersion(),
        metrics.getFileSizeBytes(),
        minViews,
        health,
        dataHealth,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        events,
        stages,
        speakers,
        stageId,
        speakerId,
        ctaData,
        null,
        eventRows);
  }

  private String formatAge(long lastMillis) {
    if (lastMillis <= 0)
      return "—";
    long secs = (System.currentTimeMillis() - lastMillis) / 1000;
    if (secs < 1)
      return "justo ahora";
    if (secs < 60)
      return "hace " + secs + " s";
    long mins = secs / 60;
    if (mins < 60)
      return "hace " + mins + " min";
    long hours = mins / 60;
    return "hace " + hours + " h";
  }

  private DataHealth computeDataHealth(boolean empty, long ageMillis, String range) {
    if (empty) {
      return new DataHealth("Sin datos", "empty", "No hay datos en este rango/segmento");
    }
    long threshold;
    switch (range) {
      case "today" -> threshold = Duration.ofMinutes(2).toMillis();
      case "7" -> threshold = Duration.ofMinutes(15).toMillis();
      case "30" -> threshold = Duration.ofMinutes(30).toMillis();
      default -> threshold = Duration.ofMinutes(60).toMillis();
    }
    if (ageMillis > threshold) {
      return new DataHealth("Desactualizado", "stale", "Hace mucho que no se actualiza");
    }
    return new DataHealth("OK", "ok", "Datos recientes");
  }

  private static class Stats {
    long views;
    long regs;
  }

  private Map<String, Stats> buildTalkStats(
      Map<String, Long> snap, String eventId, String stageId, String speakerId) {
    Map<String, Stats> stats = new HashMap<>();
    Map<String, Long> views = extractMap(snap, "talk_view:");
    Map<String, Long> regs = extractMap(snap, "talk_register:");
    Set<String> ids = new HashSet<>();
    ids.addAll(views.keySet());
    ids.addAll(regs.keySet());
    for (String id : ids) {
      Talk t = eventService.findTalk(id);
      if (t == null)
        continue;
      if (eventId != null && !eventId.isBlank()) {
        com.scanales.eventflow.model.Event ev = eventService.findEventByTalk(id);
        if (ev == null || !eventId.equals(ev.getId()))
          continue;
      }
      if (stageId != null && !stageId.isBlank()) {
        if (t.getLocation() == null || !stageId.equals(t.getLocation()))
          continue;
      }
      if (speakerId != null && !speakerId.isBlank()) {
        boolean match = t.getSpeakers().stream().anyMatch(s -> speakerId.equals(s.getId()));
        if (!match)
          continue;
      }
      Stats s = stats.computeIfAbsent(id, k -> new Stats());
      s.views = views.getOrDefault(id, 0L);
      s.regs = regs.getOrDefault(id, 0L);
    }
    return stats;
  }

  private Map<String, Stats> aggregateSpeakers(Map<String, Stats> talkStats) {
    Map<String, Stats> map = new HashMap<>();
    for (Map.Entry<String, Stats> e : talkStats.entrySet()) {
      Talk t = eventService.findTalk(e.getKey());
      if (t == null)
        continue;
      for (Speaker sp : t.getSpeakers()) {
        if (sp != null && sp.getId() != null) {
          Stats s = map.computeIfAbsent(sp.getId(), k -> new Stats());
          s.views += e.getValue().views;
          s.regs += e.getValue().regs;
        }
      }
    }
    return map;
  }

  private Map<String, Stats> aggregateScenarios(Map<String, Stats> talkStats) {
    Map<String, Stats> map = new HashMap<>();
    for (Map.Entry<String, Stats> e : talkStats.entrySet()) {
      Talk t = eventService.findTalk(e.getKey());
      if (t == null || t.getLocation() == null)
        continue;
      Stats s = map.computeIfAbsent(t.getLocation(), k -> new Stats());
      s.views += e.getValue().views;
      s.regs += e.getValue().regs;
    }
    return map;
  }

  private Map<String, Stats> aggregateEvents(Map<String, Stats> talkStats) {
    Map<String, Stats> map = new HashMap<>();
    for (Map.Entry<String, Stats> e : talkStats.entrySet()) {
      com.scanales.eventflow.model.Event ev = eventService.findEventByTalk(e.getKey());
      if (ev == null)
        continue;
      Stats s = map.computeIfAbsent(ev.getId(), k -> new Stats());
      s.views += e.getValue().views;
      s.regs += e.getValue().regs;
    }
    return map;
  }

  private CtaData buildCtaData(Map<String, Long> snap, LocalDate start) {
    Map<LocalDate, long[]> perDay = new HashMap<>();
    long totalReleases = 0, totalIssues = 0, totalKofi = 0;
    for (Map.Entry<String, Long> e : snap.entrySet()) {
      if (e.getKey().startsWith("cta:")) {
        String[] parts = e.getKey().split(":");
        if (parts.length == 3) {
          LocalDate d = LocalDate.parse(parts[2]);
          if (!d.isBefore(start)) {
            long[] arr = perDay.computeIfAbsent(d, k -> new long[3]);
            switch (parts[1]) {
              case "releases" -> {
                arr[0] += e.getValue();
                totalReleases += e.getValue();
              }
              case "issues" -> {
                arr[1] += e.getValue();
                totalIssues += e.getValue();
              }
              case "kofi" -> {
                arr[2] += e.getValue();
                totalKofi += e.getValue();
              }
            }
          }
        }
      }
    }
    int activeDays = perDay.size();
    List<CtaDayRow> rows = perDay.entrySet().stream()
        .map(
            e -> {
              long rel = e.getValue()[0];
              long iss = e.getValue()[1];
              long kof = e.getValue()[2];
              long total = rel + iss + kof;
              return new CtaDayRow(e.getKey(), rel, iss, kof, total, false);
            })
        .sorted(Comparator.comparing(CtaDayRow::date).reversed())
        .collect(Collectors.toList());
    double meanTotal = activeDays > 0 ? rows.stream().mapToLong(CtaDayRow::total).average().orElse(0) : 0;
    double std = 0;
    if (activeDays > 0) {
      double m = meanTotal;
      std = Math.sqrt(rows.stream().mapToDouble(r -> Math.pow(r.total - m, 2)).sum() / activeDays);
    }
    double avgRel = activeDays > 0 ? (double) totalReleases / activeDays : 0;
    double avgIss = activeDays > 0 ? (double) totalIssues / activeDays : 0;
    double avgKof = activeDays > 0 ? (double) totalKofi / activeDays : 0;
    Set<LocalDate> peaks = rows.stream()
        .sorted(Comparator.comparingLong(CtaDayRow::total).reversed())
        .limit(3)
        .map(CtaDayRow::date)
        .collect(Collectors.toSet());
    int peakCount = peaks.size();
    rows = rows.stream()
        .map(
            r -> new CtaDayRow(
                r.date(),
                r.releases(),
                r.issues(),
                r.kofi(),
                r.total(),
                peaks.contains(r.date())))
        .collect(Collectors.toList());
    return new CtaData(
        totalReleases,
        totalIssues,
        totalKofi,
        avgRel,
        avgIss,
        avgKof,
        meanTotal,
        std,
        activeDays,
        rows,
        peakCount);
  }

  private List<ConversionRow> topConversionRows(
      Map<String, Stats> map, int limit, Function<String, String> nameFn, int threshold) {
    return map.entrySet().stream()
        .filter(e -> e.getValue().views >= threshold)
        .map(
            e -> {
              Stats s = e.getValue();
              return new ConversionRow(
                  e.getKey(),
                  nameFn.apply(e.getKey()),
                  s.views,
                  s.regs,
                  formatConversion(s.views, s.regs));
            })
        .sorted((a, b) -> Double.compare(conversionRate(b), conversionRate(a)))
        .limit(limit)
        .collect(Collectors.toList());
  }

  private List<ConversionRow> filterRows(
      List<ConversionRow> rows, String query, String sort, String dir) {
    var stream = rows.stream();
    if (query != null && !query.isBlank()) {
      String q = query.toLowerCase();
      stream = stream.filter(r -> r.name().toLowerCase().contains(q));
    }
    Comparator<ConversionRow> cmp;
    if ("regs".equalsIgnoreCase(sort)) {
      cmp = Comparator.comparingLong(ConversionRow::registrations);
    } else if ("name".equalsIgnoreCase(sort)) {
      cmp = Comparator.comparing(ConversionRow::name, String.CASE_INSENSITIVE_ORDER);
    } else {
      cmp = Comparator.comparingLong(ConversionRow::views);
    }
    cmp = cmp.thenComparing(ConversionRow::name, String.CASE_INSENSITIVE_ORDER);
    if (!"asc".equalsIgnoreCase(dir)) {
      cmp = cmp.reversed();
    }
    return stream.sorted(cmp).collect(Collectors.toList());
  }

  private List<CtaDayRow> filterCtaRows(List<CtaDayRow> rows, String query) {
    if (query == null || query.isBlank()) {
      return rows;
    }
    String q = query.toLowerCase();
    return rows.stream()
        .filter(
            r -> r.date().toString().contains(q)
                || ("releases".contains(q) && r.releases() > 0)
                || ("issues".contains(q) && r.issues() > 0)
                || (("ko-fi".contains(q) || "kofi".contains(q)) && r.kofi() > 0))
        .collect(Collectors.toList());
  }

  private static double conversionRate(ConversionRow r) {
    return r.views() == 0 ? 0d : (double) r.registrations() / r.views();
  }

  private static String formatConversion(long views, long regs) {
    if (views == 0)
      return "—";
    return String.format(Locale.US, "%.1f%%", regs * 100.0 / views);
  }

  private static String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    String escaped = value.replace("\"", "\"\"");
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + escaped + "\"";
    }
    return escaped;
  }

  private boolean belongsToEvent(String stageId, String eventId) {
    com.scanales.eventflow.model.Event ev = eventService.findEventByScenario(stageId);
    return ev != null && eventId.equals(ev.getId());
  }

  private static long sumByPrefix(Map<String, Long> data, String prefix) {
    return data.entrySet().stream()
        .filter(e -> e.getKey().startsWith(prefix))
        .mapToLong(Map.Entry::getValue)
        .sum();
  }

  private static Map<String, Long> extractMap(Map<String, Long> data, String prefix) {
    return data.entrySet().stream()
        .filter(e -> e.getKey().startsWith(prefix))
        .collect(Collectors.toMap(e -> e.getKey().substring(prefix.length()), Map.Entry::getValue));
  }

  private String talkName(String id) {
    Talk t = eventService.findTalk(id);
    return t != null && t.getName() != null ? t.getName() : id;
  }

  private String speakerName(String id) {
    Speaker s = speakerService.getSpeaker(id);
    return s != null && s.getName() != null ? s.getName() : id;
  }

  private String stageName(String id) {
    Scenario sc = eventService.findScenario(id);
    return sc != null && sc.getName() != null ? sc.getName() : id;
  }
}
