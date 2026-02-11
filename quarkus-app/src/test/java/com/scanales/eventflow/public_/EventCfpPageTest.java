package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class EventCfpPageTest {

  @Inject EventService eventService;

  private static final String EVENT_ID = "cfp-page-event";

  @AfterEach
  public void cleanup() {
    eventService.deleteEvent(EVENT_ID);
  }

  @Test
  @TestSecurity(user = "member@example.com")
  public void cfpPageRendersForExistingEvent() {
    Event event = new Event(EVENT_ID, "CFP Event", "desc");
    eventService.saveEvent(event);

    given()
        .when()
        .get("/event/" + EVENT_ID + "/cfp")
        .then()
        .statusCode(200)
        .body(containsString("id=\"cfpForm\""))
        .body(containsString("/api/events/"))
        .body(containsString("/cfp/submissions"));
  }

  @Test
  public void cfpPageShowsLoginPromptWhenAnonymous() {
    Event event = new Event(EVENT_ID, "CFP Event", "desc");
    eventService.saveEvent(event);

    given()
        .when()
        .get("/event/" + EVENT_ID + "/cfp")
        .then()
        .statusCode(200)
        .body(not(containsString("id=\"cfpForm\"")))
        .body(containsString("Login to submit a proposal for this event."));
  }

  @Test
  public void cfpPageShowsNotFoundStateWhenEventMissing() {
    given()
        .when()
        .get("/event/missing-event-for-cfp/cfp")
        .then()
        .statusCode(200)
        .body(containsString("event_busy"))
        .body(not(containsString("id=\"cfpForm\"")));
  }
}
