package com.scanales.eventflow.public_;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.SpeakerService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SpeakerResourceTalkEventsTest {

  @Inject EventService eventService;

  @Inject SpeakerService speakerService;

  private static final String EVENT_ID = "e-speaker";
  private static final String SPEAKER_ID = "s-speaker";
  private static final String TALK_ID = "t-speaker";

  @BeforeEach
  public void setup() {
    Speaker speaker = new Speaker(SPEAKER_ID, "Speaker");
    Talk talk = new Talk(TALK_ID, "Charla test");
    talk.setSpeakers(List.of(speaker));
    speaker.getTalks().add(talk);
    speakerService.saveSpeaker(speaker);

    Event event = new Event(EVENT_ID, "Evento de prueba", "desc");
    Talk eventTalk = new Talk(TALK_ID, "Charla test");
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
  public void speakerViewShowsEventForTalk() {
    given()
        .when()
        .get("/speaker/" + SPEAKER_ID)
        .then()
        .statusCode(200)
        .body(containsString("Evento de prueba"));
  }
}
