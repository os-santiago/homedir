package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.service.EventService;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class TalkResourceTest {

    @Inject
    EventService eventService;

    private static final String EVENT_ID = "e1";
    private static final String TALK_ID = "t1";

    @BeforeEach
    public void setup() {
        Event event = new Event(EVENT_ID, "Evento", "desc");
        Talk talk = new Talk(TALK_ID, "Charla de prueba");
        talk.setSpeakers(List.of(new Speaker("s1", "Speaker")));
        talk.setStartTime(LocalTime.of(10, 0));
        talk.setDurationMinutes(60);
        event.getAgenda().add(talk);
        eventService.saveEvent(event);
    }

    @AfterEach
    public void cleanup() {
        eventService.deleteEvent(EVENT_ID);
    }

    @Test
    public void anonymousUserCanViewTalk() {
        given()
            .when().get("/talk/" + TALK_ID)
            .then()
            .statusCode(200)
            .body(containsString("Charla de prueba"));
    }
}
