package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
            .body(containsString("Past events"))
            .body(containsString("Upcoming events"))
            .extract()
            .asString();

    int upcomingIdx = html.indexOf("Upcoming events");
    int pastIdx = html.indexOf("Past events");
    int updatedIdx = html.indexOf("Updated");

    List<Event> events = eventService.listEvents();
    int expectedUpcoming = countUpcoming(events);
    int expectedPast = countPast(events);

    Assertions.assertEquals(expectedUpcoming, extractStatValue(html, "Upcoming events"));
    Assertions.assertEquals(expectedPast, extractStatValue(html, "Past events"));
    Assertions.assertTrue(upcomingIdx >= 0, "Upcoming events section should render");
    Assertions.assertTrue(pastIdx > upcomingIdx, "Past section should follow upcoming stats");
    Assertions.assertTrue(updatedIdx > pastIdx, "Stats section order should end with Updated");
  }

  private static int extractStatValue(String html, String label) {
    String marker = "<span class=\"label\">" + label + "</span>";
    int labelIdx = html.indexOf(marker);
    Assertions.assertTrue(labelIdx >= 0, label + " label should be present on the page");
    int valueStart = html.indexOf("<span class=\"value\">", labelIdx);
    Assertions.assertTrue(valueStart >= 0, "value span missing for " + label);
    int valueEnd = html.indexOf("</span>", valueStart);
    Assertions.assertTrue(valueEnd >= 0, "value span not closed for " + label);
    String valueText = html.substring(valueStart + "<span class=\"value\">".length(), valueEnd).trim();
    return Integer.parseInt(valueText);
  }

  private static int countUpcoming(List<Event> events) {
    LocalDate today = LocalDate.now();
    return (int)
        events.stream()
            .filter(
                e -> {
                  var end = e.getEndDateTime();
                  LocalDate endDate = end == null ? null : end.toLocalDate();
                  return endDate == null || !endDate.isBefore(today);
                })
            .count();
  }

  private static int countPast(List<Event> events) {
    LocalDate today = LocalDate.now();
    return (int)
        events.stream()
            .filter(
                e -> {
                  var end = e.getEndDateTime();
                  LocalDate endDate = end == null ? null : end.toLocalDate();
                  return endDate != null && endDate.isBefore(today);
                })
            .count();
  }
}
