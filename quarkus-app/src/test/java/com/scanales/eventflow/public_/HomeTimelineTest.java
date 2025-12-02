package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HomeTimelineTest {

  @Inject EventService eventService;

  @BeforeEach
  public void setup() {
    Event first = new Event("home1", "Evento Cercano", "desc");
    first.setDate(LocalDate.now().plusDays(3));
    Event second = new Event("home2", "Evento Lejano", "desc");
    second.setDate(LocalDate.now().plusDays(10));
    eventService.saveEvent(first);
    eventService.saveEvent(second);
  }

  @AfterEach
  public void cleanup() {
    eventService.listEvents().forEach(e -> eventService.deleteEvent(e.getId()));
  }

  @Test
  public void homeShowsEventsOrderedWithCountdown() {
    List<Event> events = eventService.listEvents();
    int expectedUpcoming = countUpcoming(events);
    String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

    String html =
        given()
            .when()
            .get("/")
            .then()
            .statusCode(200)
            .body(containsString("Events & Experiences"))
            .body(containsString("Community Campus"))
            .body(containsString("Updated"))
            .extract()
            .asString();

    Assertions.assertTrue(
        html.contains(expectedUpcoming + " events coming up"),
        "Upcoming banner should reflect the number of future events");
    Assertions.assertEquals(
        expectedUpcoming, extractStatValue(html, "Upcoming events"), "Upcoming stats should match");
    Assertions.assertTrue(html.contains("events this month"));
    Assertions.assertTrue(html.contains(today), "Updated badge should show current date");
  }

  private static int extractStatValue(String html, String label) {
    String marker = "<span class=\"label\">" + label + "</span>";
    int labelIdx = html.indexOf(marker);
    Assertions.assertTrue(labelIdx >= 0, label + " label should be present");
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
}
