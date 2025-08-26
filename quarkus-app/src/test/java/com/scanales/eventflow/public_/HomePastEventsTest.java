package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HomePastEventsTest {

  @Inject EventService eventService;

  @BeforeEach
  public void setup() {
    Event past = new Event("past1", "Evento Pasado", "desc");
    past.setDate(LocalDate.now().minusDays(1));
    Event upcoming = new Event("up1", "Evento Futuro", "desc");
    upcoming.setDate(LocalDate.now().plusDays(1));
    eventService.saveEvent(past);
    eventService.saveEvent(upcoming);
  }

  @AfterEach
  public void cleanup() {
    eventService.listEvents().forEach(e -> eventService.deleteEvent(e.getId()));
  }

  @Test
  public void homeShowsPastSection() {
    String html =
        given()
            .when()
            .get("/")
            .then()
            .statusCode(200)
            .body(containsString("Eventos pasados"))
            .extract()
            .asString();

    int pastIdx = html.indexOf("Eventos pasados");
    int pastEventIdx = html.indexOf("Evento Pasado");
    int upcomingIdx = html.indexOf("Eventos disponibles");
    int upcomingEventIdx = html.indexOf("Evento Futuro");

    org.junit.jupiter.api.Assertions.assertTrue(upcomingEventIdx > upcomingIdx);
    org.junit.jupiter.api.Assertions.assertTrue(pastEventIdx > pastIdx);
    org.junit.jupiter.api.Assertions.assertTrue(upcomingEventIdx < pastIdx);
  }
}
