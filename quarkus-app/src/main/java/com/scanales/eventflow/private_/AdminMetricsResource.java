package com.scanales.eventflow.private_;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.SpeakerService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UsageMetricsService.Summary;
import com.scanales.eventflow.service.UsageMetricsService.Health;
import com.scanales.eventflow.util.AdminUtils;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Admin page for usage metrics with simple reporting and CSV export. */
@Path("/private/admin/metrics")
public class AdminMetricsResource {

    public record ConversionRow(String name, long views, long registrations, String conversion) {}

    public record MetricsData(
            long eventsViewed,
            long talksViewed,
            long talksRegistered,
            long stageVisits,
            String lastUpdate,
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
            Health health
    ) {}

    /** Row representing registrations for a talk. */
    public record TalkRegistrationRow(String id, String name, long registrations) {}

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance index(MetricsData data);
        static native TemplateInstance guide();
        static native TemplateInstance talks(Event event, List<TalkRegistrationRow> rows);
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
    public Response metrics(@QueryParam("range") String range, @QueryParam("event") String eventId) {
        if (!AdminUtils.isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        MetricsData data = buildData(range, eventId);
        return Response.ok(Templates.index(data)).build();
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
    public Response export(@QueryParam("table") String table, @QueryParam("range") String range, @QueryParam("event") String eventId) {
        if (!AdminUtils.isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        MetricsData data = buildData(range, eventId);
        List<ConversionRow> rows;
        String header;
        switch (table) {
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
        StringBuilder sb = new StringBuilder();
        if (!header.isEmpty()) {
            sb.append(header).append('\n');
            for (ConversionRow r : rows) {
                sb.append(r.name()).append(',').append(r.views()).append(',')
                        .append(r.registrations()).append(',').append(r.conversion()).append('\n');
            }
        }
        return Response.ok(sb.toString())
                .header("Content-Disposition", "attachment; filename=metrics-" + table + ".csv")
                .build();
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
        Map<String, Long> snap = metrics.snapshot();
        List<TalkRegistrationRow> rows = event.getAgenda().stream()
                .map(t -> {
                    String name = t.getName() != null ? t.getName() : t.getId();
                    return new TalkRegistrationRow(t.getId(), name,
                            snap.getOrDefault("talk_register:" + t.getId(), 0L));
                })
                .sorted(java.util.Comparator.comparingLong(TalkRegistrationRow::registrations).reversed())
                .toList();
        return Response.ok(Templates.talks(event, rows)).build();
    }

    @ConfigProperty(name = "metrics.min-view-threshold", defaultValue = "20")
    int minViews;

    private MetricsData buildData(String range, String eventId) {
        Map<String, Long> snap = metrics.snapshot();
        Summary summary = metrics.getSummary();
        Health health = metrics.getHealth();
        boolean empty = snap.isEmpty();

        long eventsViewed;
        if (eventId != null && !eventId.isBlank()) {
            eventsViewed = snap.getOrDefault("event_view:" + eventId, 0L);
        } else {
            eventsViewed = sumByPrefix(snap, "event_view:");
        }

        Map<String, Stats> talkStats = buildTalkStats(snap, eventId);
        long talksViewed = talkStats.values().stream().mapToLong(s -> s.views).sum();
        long talksRegistered = talkStats.values().stream().mapToLong(s -> s.regs).sum();

        LocalDate today = LocalDate.now();
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
                        String stageId = parts[1];
                        if (eventId == null || belongsToEvent(stageId, eventId)) {
                            stageMap.merge(stageId, e.getValue(), Long::sum);
                        }
                    }
                }
            }
        }
        long stageVisits = stageMap.values().stream().mapToLong(Long::longValue).sum();

        List<ConversionRow> topTalks = topConversionRows(talkStats, 10, this::talkName);
        Map<String, Stats> speakerStats = aggregateSpeakers(talkStats);
        List<ConversionRow> topSpeakers = topConversionRows(speakerStats, 10, this::speakerName);
        Map<String, Stats> scenarioStats = aggregateScenarios(talkStats);
        List<ConversionRow> topScenarios = topConversionRows(scenarioStats, 10, this::stageName);

        long last = metrics.getLastUpdatedMillis();
        String lastStr = last > 0 ? Instant.ofEpochMilli(last).toString() : "—";
        String globalConv = formatConversion(talksViewed, talksRegistered);
        long expectedAttendees = talksRegistered;

        return new MetricsData(eventsViewed, talksViewed, talksRegistered, stageVisits,
                lastStr, summary.discarded(), metrics.getConfig(), topTalks, topSpeakers, topScenarios,
                globalConv, expectedAttendees, empty, range, eventId, metrics.getSchemaVersion(),
                metrics.getFileSizeBytes(), minViews, health);
    }

    private static class Stats { long views; long regs; }

    private Map<String, Stats> buildTalkStats(Map<String, Long> snap, String eventId) {
        Map<String, Stats> stats = new HashMap<>();
        Map<String, Long> views = extractMap(snap, "talk_view:");
        Map<String, Long> regs = extractMap(snap, "talk_register:");
        Set<String> ids = new HashSet<>();
        ids.addAll(views.keySet());
        ids.addAll(regs.keySet());
        for (String id : ids) {
            Talk t = eventService.findTalk(id);
            if (t == null) continue;
            if (eventId != null && !eventId.isBlank()) {
                com.scanales.eventflow.model.Event ev = eventService.findEventByTalk(id);
                if (ev == null || !eventId.equals(ev.getId())) continue;
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
            if (t == null) continue;
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
            if (t == null || t.getLocation() == null) continue;
            Stats s = map.computeIfAbsent(t.getLocation(), k -> new Stats());
            s.views += e.getValue().views;
            s.regs += e.getValue().regs;
        }
        return map;
    }

    private List<ConversionRow> topConversionRows(Map<String, Stats> map, int limit, Function<String, String> nameFn) {
        return map.entrySet().stream()
                .filter(e -> e.getValue().views >= minViews)
                .map(e -> {
                    Stats s = e.getValue();
                    return new ConversionRow(nameFn.apply(e.getKey()), s.views, s.regs,
                            formatConversion(s.views, s.regs));
                })
                .sorted((a, b) -> Double.compare(conversionRate(b), conversionRate(a)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static double conversionRate(ConversionRow r) {
        return r.views() == 0 ? 0d : (double) r.registrations() / r.views();
    }

    private static String formatConversion(long views, long regs) {
        if (views == 0) return "—";
        return String.format(Locale.US, "%.1f%%", regs * 100.0 / views);
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
