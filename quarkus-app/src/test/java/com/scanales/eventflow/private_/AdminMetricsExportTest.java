package com.scanales.eventflow.private_;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

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

        metrics.recordTalkView("t1", "sess", "ua");
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

        assertTrue(csv.contains("\"DevOps y Platform Engineering: Amigos, enemigos o algo más?\""));
        assertTrue(csv.contains(",1,1,"));
    }
}

