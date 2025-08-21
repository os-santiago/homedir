package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UserScheduleService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
  public class TalkResourceTest {

  @Inject EventService eventService;
  @Inject UserScheduleService userSchedule;

  private static final String EVENT_ID = "e1";
  private static final String TALK_ID = "s1-talk-1";

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
    userSchedule.removeTalkForUser("user@example.com", TALK_ID);
  }

  @Test
  public void anonymousUserCanViewTalk() {
    given()
        .when()
        .get("/talk/" + TALK_ID)
        .then()
        .statusCode(200)
        .body(containsString("Charla de prueba"));
  }

  @Test
  public void talkUrlWithSlugIsResolved() {
    given()
        .when()
        .get("/event/" + EVENT_ID + "/talk/" + TALK_ID + "-extra")
        .then()
        .statusCode(200)
        .body(containsString("Charla de prueba"));
  }

  @Test
  public void qrRedirectsAnonymousToLogin() {
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/talk/" + TALK_ID + "?qr=1")
        .then()
        .statusCode(303)
        .header("Location", containsString("/login?redirect="))
        .header("Location", not(containsString("qr=1")));
  }

  @Test
  @TestSecurity(user = "user@example.com")
  public void qrAddsTalkAndMarksAttended() {
    assertFalse(
        userSchedule.getTalkDetailsForUser("user@example.com").containsKey(TALK_ID));
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/talk/" + TALK_ID + "?qr=1")
        .then()
        .statusCode(303)
        .header("Location", containsString("/profile"));
    var details = userSchedule.getTalkDetailsForUser("user@example.com").get(TALK_ID);
    assertNotNull(details);
    assertTrue(details.attended);
  }

  @Test
  @TestSecurity(user = "user@example.com")
  public void qrAddsTalkAndMarksAttendedFromEvent() {
    assertFalse(
        userSchedule.getTalkDetailsForUser("user@example.com").containsKey(TALK_ID));
    given()
        .redirects()
        .follow(false)
        .when()
        .get("/event/" + EVENT_ID + "/talk/" + TALK_ID + "?qr=1")
        .then()
        .statusCode(303)
        .header("Location", containsString("/profile"));
    var details = userSchedule.getTalkDetailsForUser("user@example.com").get(TALK_ID);
    assertNotNull(details);
    assertTrue(details.attended);
  }
}
