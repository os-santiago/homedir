package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.scanales.eventflow.model.Event;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class EventServiceSeedTest {

  @Inject EventService eventService;

  @BeforeEach
  void setup() {
    eventService.reset();
  }

  @Test
  void saveDevOpsDaysEventSeedsDraftAgendaWhenAgendaIsEmpty() {
    Event event =
        new Event(
            "devopsdays-santiago-2026",
            "DevOpsDays Santiago 2026",
            "Seed test event");
    event.setDate(LocalDate.parse("2026-10-15"));
    eventService.saveEvent(event);

    Event stored = eventService.getEvent("devopsdays-santiago-2026");
    assertNotNull(stored);
    assertFalse(stored.getAgenda().isEmpty());
    assertFalse(stored.getScenarios().isEmpty());
  }
}

