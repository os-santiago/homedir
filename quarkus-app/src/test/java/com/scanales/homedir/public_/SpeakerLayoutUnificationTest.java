package com.scanales.homedir.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.homedir.model.Event;
import com.scanales.homedir.model.Speaker;
import com.scanales.homedir.model.Talk;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.SpeakerService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SpeakerLayoutUnificationTest {

  @Inject SpeakerService speakerService;

  @Inject EventService eventService;

  private static final String EVENT_ID = "e-layout-unif";
  private static final String SPEAKER_ID = "s-layout-unif";
  private static final String TALK_ID = "t-layout-unif";

  @BeforeEach
  public void setup() {
    Speaker speaker = new Speaker(SPEAKER_ID, "Speaker Unificado");
    Talk talk = new Talk(TALK_ID, "Charla de prueba layout");
    talk.setSpeakers(List.of(speaker));
    speaker.getTalks().add(talk);
    speakerService.saveSpeaker(speaker);

    Event event = new Event(EVENT_ID, "Evento Layout", "desc");
    event.setThemePrimaryColor("#111111");
    event.setThemeAccentColor("#222222");
    event.setThemeSurfaceColor("#0f0f0f");
    event.setThemeTextColor("#ffffff");
    Talk eventTalk = new Talk(TALK_ID, "Charla de prueba layout");
    eventTalk.setSpeakers(List.of(speaker));
    eventTalk.setStartTime(LocalTime.of(10, 0));
    eventTalk.setDurationMinutes(60);
    event.getAgenda().add(eventTalk);
    eventService.saveEvent(event);
  }

  @AfterEach
  public void cleanup() {
    eventService.deleteEvent(EVENT_ID);
    speakerService.deleteSpeaker(SPEAKER_ID);
  }

  @Test
  void speakerPageUsesMainLayoutWithBreadcrumbs() {
    given()
        .when()
        .get("/speaker/" + SPEAKER_ID)
        .then()
        .statusCode(200)
        .body(containsString("homedir-main"))
        .body(containsString("Speaker Unificado"))
        .body(containsString("Orador: Speaker Unificado"));
  }

  @Test
  void speakerPageRendersRetroThemeAssets() {
    given()
        .when()
        .get("/speaker/" + SPEAKER_ID + "?event=" + EVENT_ID)
        .then()
        .statusCode(200)
        .body(containsString("Press+Start+2P"))
        .body(containsString("--event-theme-primary: #111111"))
        .body(containsString("--event-theme-accent: #222222"))
        .body(containsString("retro-theme.js"));
  }

  @Test
  void speakerPageContainsMainLayoutStructure() {
    given()
        .when()
        .get("/speaker/" + SPEAKER_ID)
        .then()
        .statusCode(200)
        .body(containsString("app-container"))
        .body(containsString("footer-styled"))
        .body(containsString("login-modal"));
  }

  @Test
  void unknownSpeakerReturnsPageWithLayout() {
    given()
        .when()
        .get("/speaker/no-existe-speaker")
        .then()
        .statusCode(200)
        .body(containsString("Speaker no encontrado"))
        .body(containsString("homedir-main"));
  }
}
