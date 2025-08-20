package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Scenario;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class EventTalkResourceTest {

  @Inject EventService eventService;

  @AfterEach
  public void cleanup() {
    eventService.deleteEvent("e1");
    eventService.deleteEvent("e2");
  }

  @Test
  public void talkUsesEventSpecificContext() {
    Event e1 = new Event("e1", "Evento A", "desc");
    e1.setScenarios(List.of(new Scenario("sc1", "Sala A")));
    Talk t1 = new Talk("s1-talk-1", "Charla");
    t1.setLocation("sc1");
    t1.setStartTime(LocalTime.of(10, 0));
    t1.setDurationMinutes(30);
    e1.getAgenda().add(t1);
    eventService.saveEvent(e1);

    Event e2 = new Event("e2", "Evento B", "desc");
    e2.setScenarios(List.of(new Scenario("sc2", "Sala B")));
    Talk t2 = new Talk("s1-talk-1", "Charla");
    t2.setLocation("sc2");
    t2.setStartTime(LocalTime.of(11, 0));
    t2.setDurationMinutes(45);
    e2.getAgenda().add(t2);
    eventService.saveEvent(e2);

    given()
        .when()
        .get("/event/e2/talk/s1-talk-1")
        .then()
        .statusCode(200)
        .body(containsString("/event/e2/scenario/sc2"));
  }
}
