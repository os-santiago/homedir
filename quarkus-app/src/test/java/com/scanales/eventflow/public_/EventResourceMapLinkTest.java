package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class EventResourceMapLinkTest {

  @Inject EventService eventService;

  private static final String EVENT_WITH_MAP = "map1";
  private static final String EVENT_WITHOUT_MAP = "map2";
  private static final String EVENT_WITH_TALK = "map3";

  @AfterEach
  public void cleanup() {
    eventService.deleteEvent(EVENT_WITH_MAP);
    eventService.deleteEvent(EVENT_WITHOUT_MAP);
    eventService.deleteEvent(EVENT_WITH_TALK);
  }

  @Test
  public void showsMapLinkWhenConfigured() {
    Event event = new Event(EVENT_WITH_MAP, "Evento con mapa", "desc");
    event.setMapUrl("https://example.com/map");
    eventService.saveEvent(event);

    given()
        .when()
        .get("/event/" + EVENT_WITH_MAP)
        .then()
        .statusCode(200)
        .body(containsString("View venue map"))
        .body(containsString("https://example.com/map"));
  }

  @Test
  public void hidesMapLinkWhenMissing() {
    Event event = new Event(EVENT_WITHOUT_MAP, "Evento sin mapa", "desc");
    eventService.saveEvent(event);

    given()
        .when()
        .get("/event/" + EVENT_WITHOUT_MAP)
        .then()
        .statusCode(200)
        .body(not(containsString("View venue map")));
  }

  @Test
  public void eventDetailTalkLinksUseCanonicalTalkRoute() {
    Event event = new Event(EVENT_WITH_TALK, "Evento con charla", "desc");
    Talk talk = new Talk("talk-canonical-1", "Talk canonical route");
    talk.setLocation("main-stage");
    talk.setStartTime(LocalTime.of(9, 0));
    talk.setDurationMinutes(30);
    event.getAgenda().add(talk);
    eventService.saveEvent(event);

    given()
        .when()
        .get("/event/" + EVENT_WITH_TALK)
        .then()
        .statusCode(200)
        .body(containsString("/talk/talk-canonical-1"))
        .body(not(containsString("/event/" + EVENT_WITH_TALK + "/talk/talk-canonical-1")));
  }
}
