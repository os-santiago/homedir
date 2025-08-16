package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.SpeakerService;
import com.scanales.eventflow.service.UsageMetricsService;

/** Tests for the CSV export in {@link AdminMetricsResource}. */
@QuarkusTest
public class AdminMetricsExportTest {

    @Inject
    EventService eventService;

    @Inject
    SpeakerService speakerService;

    @Inject
    UsageMetricsService metrics;

    @BeforeEach
    void setUp() {
        try {
            Method m = UsageMetricsService.class.getDeclaredMethod("reset");
            m.setAccessible(true);
            m.invoke(metrics);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Basic event with a single talk and speaker
        Speaker sp = new Speaker();
        sp.setId("sp1");
        sp.setName("Alice");
        speakerService.saveSpeaker(sp);

        Talk talk = new Talk();
        talk.setId("t1");
        talk.setName("DevOps y Platform Engineering: Amigos, enemigos o algo más?");
        talk.setLocation("st1");
        talk.setSpeakers(List.of(sp));

        Scenario sc = new Scenario("st1", "Stage");

        Event ev = new Event("ev1", "Event", "Desc");
        ev.getScenarios().add(sc);
        ev.getAgenda().add(talk);
        eventService.saveEvent(ev);

        metrics.recordTalkView("t1", null, "ua");
        metrics.recordTalkRegister("t1", List.of(sp), "ua");
    }

    @Test
    @TestSecurity(user = "sergio.canales.e@gmail.com")
    public void exportTalksHeaderWhenNoRows() {
        String csv = given()
                .when().get("/private/admin/metrics/export?table=talks&q=nomatch")
                .then().statusCode(200)
                .extract().asString();

        // The CSV must contain the header even when there are no data rows
        assertTrue(csv.startsWith("Charla,Vistas,Registros,Conversion"));
    }

    @Test
    @TestSecurity(user = "sergio.canales.e@gmail.com")
    public void exportTalksIncludesLowViewRows() {
        String csv = given()
                .when().get("/private/admin/metrics/export?table=talks")
                .then().statusCode(200)
                .extract().asString();

        String[] lines = csv.split("\\R");
        java.util.List<String> nonBlank = java.util.Arrays.stream(lines)
                .filter(l -> !l.isBlank())
                .toList();
        assertTrue(nonBlank.size() > 1, csv);
        java.util.List<String> cols = parseCsvLine(nonBlank.get(1));
        assertEquals("DevOps y Platform Engineering: Amigos, enemigos o algo más?", cols.get(0));
        assertEquals("1", cols.get(1));
        assertEquals("1", cols.get(2));
    }

    private static java.util.List<String> parseCsvLine(String line) {
        java.util.List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        fields.add(sb.toString());
        return fields;
    }
}

