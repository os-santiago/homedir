package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class EventResourceMapLinkTest {

    @Inject
    EventService eventService;

    private static final String EVENT_WITH_MAP = "map1";
    private static final String EVENT_WITHOUT_MAP = "map2";

    @AfterEach
    public void cleanup() {
        eventService.deleteEvent(EVENT_WITH_MAP);
        eventService.deleteEvent(EVENT_WITHOUT_MAP);
    }

    @Test
    public void showsMapLinkWhenConfigured() {
        Event event = new Event(EVENT_WITH_MAP, "Evento con mapa", "desc");
        event.setMapUrl("https://example.com/map");
        eventService.saveEvent(event);

        given()
            .when().get("/event/" + EVENT_WITH_MAP)
            .then()
            .statusCode(200)
            .body(containsString("Ver mapa"))
            .body(containsString("https://example.com/map"));
    }

    @Test
    public void hidesMapLinkWhenMissing() {
        Event event = new Event(EVENT_WITHOUT_MAP, "Evento sin mapa", "desc");
        eventService.saveEvent(event);

        given()
            .when().get("/event/" + EVENT_WITHOUT_MAP)
            .then()
            .statusCode(200)
            .body(not(containsString("Ver mapa")));
    }
}

