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
public class EventScenarioResourceTest {

  @Inject EventService eventService;

  @AfterEach
  public void cleanup() {
    eventService.deleteEvent("e1");
  }

  @Test
  public void scenarioUsesEventSpecificContext() {
    Event e1 = new Event("e1", "Evento A", "desc");
    e1.setScenarios(List.of(new Scenario("sc1", "Sala A")));
    Talk t1 = new Talk("t1", "Charla");
    t1.setLocation("sc1");
    t1.setStartTime(LocalTime.of(10, 0));
    t1.setDurationMinutes(30);
    e1.getAgenda().add(t1);
    eventService.saveEvent(e1);

    given()
        .when()
        .get("/event/e1/scenario/sc1")
        .then()
        .statusCode(200)
        .body(containsString("Sala A"))
        .body(containsString("/talk/t1"));
  }
}
