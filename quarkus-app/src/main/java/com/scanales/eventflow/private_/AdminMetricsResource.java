package com.scanales.eventflow.private_;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.SpeakerService;
import com.scanales.eventflow.service.UsageMetricsService;
import com.scanales.eventflow.service.UsageMetricsService.Summary;
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

/** Admin page for usage metrics with simple reporting and CSV export. */
@Path("/private/admin/metrics")
public class AdminMetricsResource {

    public record Row(String name, long value) {}

    public record MetricsData(
            long eventsViewed,
            long talksViewed,
            long talksRegistered,
            long stageVisits,
            String lastUpdate,
            Map<String, Long> discards,
            UsageMetricsService.Config config,
            List<Row> topRegistrations,
            List<Row> topViews,
            List<Row> topSpeakers,
            List<Row> stageVisitsTable,
            boolean empty,
            String range
    ) {}

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance index(MetricsData data);
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
    public Response metrics(@QueryParam("range") String range) {
        if (!AdminUtils.isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        MetricsData data = buildData(range);
        return Response.ok(Templates.index(data)).build();
    }

    @GET
    @Path("export")
    @Authenticated
    @Produces("text/csv")
    public Response export(@QueryParam("table") String table, @QueryParam("range") String range) {
        if (!AdminUtils.isAdmin(identity)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        MetricsData data = buildData(range);
        List<Row> rows;
        String header;
        switch (table) {
            case "registrations" -> {
                rows = data.topRegistrations();
                header = "Charla,Registros";
            }
            case "views" -> {
                rows = data.topViews();
                header = "Charla,Vistas";
            }
            case "speakers" -> {
                rows = data.topSpeakers();
                header = "Orador,Registros";
            }
            case "stages" -> {
                rows = data.stageVisitsTable();
                header = "Escenario,Visitas";
            }
            default -> {
                rows = List.of();
                header = "";
            }
        }
        StringBuilder sb = new StringBuilder();
        if (!header.isEmpty()) {
            sb.append(header).append('\n');
            for (Row r : rows) {
                sb.append(r.name()).append(',').append(r.value()).append('\n');
            }
        }
        return Response.ok(sb.toString())
                .header("Content-Disposition", "attachment; filename=metrics-" + table + ".csv")
                .build();
    }

    private MetricsData buildData(String range) {
        Map<String, Long> snap = metrics.snapshot();
        Summary summary = metrics.getSummary();
        boolean empty = snap.isEmpty();
        long eventsViewed = sumByPrefix(snap, "event_view:");
        long talksViewed = sumByPrefix(snap, "talk_view:");
        long talksRegistered = sumByPrefix(snap, "talk_register:");

        Map<String, Long> regMap = extractMap(snap, "talk_register:");
        List<Row> topRegs = topRows(regMap, 10, this::talkName);
        Map<String, Long> viewMap = extractMap(snap, "talk_view:");
        List<Row> topViews = topRows(viewMap, 10, this::talkName);
        Map<String, Long> speakerMap = extractMap(snap, "speaker_popularity:");
        List<Row> topSpeakers = topRows(speakerMap, 10, this::speakerName);

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
                        stageMap.merge(parts[1], e.getValue(), Long::sum);
                    }
                }
            }
        }
        long stageVisits = stageMap.values().stream().mapToLong(Long::longValue).sum();
        List<Row> stageTable = topRows(stageMap, 10, this::stageName);

        long last = metrics.getLastUpdatedMillis();
        String lastStr = last > 0 ? Instant.ofEpochMilli(last).toString() : "—";
        return new MetricsData(eventsViewed, talksViewed, talksRegistered, stageVisits,
                lastStr, summary.discarded(), metrics.getConfig(), topRegs, topViews, topSpeakers, stageTable, empty, range);
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

    private List<Row> topRows(Map<String, Long> map, int limit, Function<String, String> nameFn) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new Row(nameFn.apply(e.getKey()), e.getValue()))
                .collect(Collectors.toList());
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
