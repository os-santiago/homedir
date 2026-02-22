package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.LocalTime;
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
    assertEquals(2, stored.getDays());

    assertTrue(
        stored.getAgenda().stream()
            .anyMatch(t -> t.getName() != null && t.getName().startsWith("Category:")));
    assertTrue(
        stored.getAgenda().stream()
            .anyMatch(t -> t.getDay() == 1 && "Welcome".equalsIgnoreCase(t.getName())));
    assertTrue(
        stored.getAgenda().stream()
            .anyMatch(
                t ->
                    t.getDay() == 1
                        && t.getName() != null
                        && t.getName().toLowerCase().contains("keynote")));
    assertTrue(
        stored.getAgenda().stream()
            .anyMatch(t -> t.getDay() == 2 && "Welcome".equalsIgnoreCase(t.getName())));
    assertTrue(
        stored.getAgenda().stream()
            .anyMatch(
                t ->
                    t.getDay() == 2
                        && t.getName() != null
                        && t.getName().toLowerCase().contains("keynote")));

    assertTrue(
        stored.getAgenda().stream()
            .allMatch(t -> t.getDurationMinutes() == 30),
        "All main stage sessions should be 30 minutes");

    LocalTime day1LastEnd =
        stored.getAgenda().stream()
            .filter(t -> t.getDay() == 1)
            .map(Talk::getEndTime)
            .filter(java.util.Objects::nonNull)
            .max(LocalTime::compareTo)
            .orElse(null);
    LocalTime day2LastEnd =
        stored.getAgenda().stream()
            .filter(t -> t.getDay() == 2)
            .map(Talk::getEndTime)
            .filter(java.util.Objects::nonNull)
            .max(LocalTime::compareTo)
            .orElse(null);

    assertEquals(LocalTime.of(16, 30), day1LastEnd);
    assertEquals(LocalTime.of(16, 30), day2LastEnd);
  }
}
