package io.eventflow.notifications.global;

import static org.junit.jupiter.api.Assertions.*;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.*;
import io.eventflow.time.SimulatedClock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class EventAndBreakEvaluatorTest {

  @Inject EventService events;
  @Inject EventStateEvaluator eventEval;
  @Inject BreakStateEvaluator breakEval;
  @Inject GlobalNotificationService global;
  @Inject SimulatedClock sim;

  @BeforeEach
  void setup() {
    events.reset();
    global.clearAll();
    sim.clear();
  }

  @Test
  void eventLifecycle() {
    Event e = new Event("e1", "Ev", "d");
    e.setDate(LocalDate.of(2023, 1, 1));
    e.setTimezone("UTC");
    Talk t1 = new Talk("t1", "t1");
    t1.setStartTime(LocalTime.of(10, 0));
    t1.setDurationMinutes(60);
    Talk t2 = new Talk("t2", "t2");
    t2.setStartTime(LocalTime.of(11, 0));
    t2.setDurationMinutes(60);
    e.getAgenda().addAll(List.of(t1, t2));
    events.saveEvent(e);

    sim.set(Instant.parse("2023-01-01T09:55:00Z"));
    eventEval.tick();
    assertTrue(count("event", "UPCOMING") >= 1);

    sim.set(Instant.parse("2023-01-01T10:00:00Z"));
    eventEval.tick();
    assertTrue(count("event", "STARTED") >= 1);

    sim.set(Instant.parse("2023-01-01T11:55:00Z"));
    eventEval.tick();
    assertTrue(count("event", "ENDING_SOON") >= 1);

    sim.set(Instant.parse("2023-01-01T12:00:00Z"));
    eventEval.tick();
    assertTrue(count("event", "FINISHED") >= 1);
  }

  @Test
  void breakLifecycle() {
    Event e = new Event("e1", "Ev", "d");
    e.setDate(LocalDate.of(2023, 1, 1));
    e.setTimezone("UTC");
    Talk b = new Talk("b1", "Coffee");
    b.setStartTime(LocalTime.of(15, 0));
    b.setDurationMinutes(15);
    b.setBreak(true);
    e.getAgenda().add(b);
    events.saveEvent(e);

    sim.set(Instant.parse("2023-01-01T14:55:00Z"));
    breakEval.tick();
    assertTrue(count("break", "UPCOMING") >= 1);

    sim.set(Instant.parse("2023-01-01T15:00:00Z"));
    breakEval.tick();
    assertTrue(count("break", "STARTED") >= 1);

    sim.set(Instant.parse("2023-01-01T15:10:00Z"));
    breakEval.tick();
    assertTrue(count("break", "ENDING_SOON") >= 1);

    sim.set(Instant.parse("2023-01-01T15:15:00Z"));
    breakEval.tick();
    assertTrue(count("break", "FINISHED") >= 1);
  }

  @Test
  void ignoresPastEvents() {
    Event e = new Event("e1", "Ev", "d");
    e.setDate(LocalDate.of(2023, 1, 1));
    e.setTimezone("UTC");
    events.saveEvent(e);

    sim.set(Instant.parse("2023-01-02T10:00:00Z"));
    eventEval.tick();
    assertEquals(0, count("event", "FINISHED"));
  }

  @Test
  void ignoresPastBreaks() {
    Event e = new Event("e1", "Ev", "d");
    e.setDate(LocalDate.of(2023, 1, 1));
    e.setTimezone("UTC");
    Talk b = new Talk("b1", "Coffee");
    b.setStartTime(LocalTime.of(10, 0));
    b.setDurationMinutes(10);
    b.setBreak(true);
    e.getAgenda().add(b);
    events.saveEvent(e);

    sim.set(Instant.parse("2023-01-02T10:00:00Z"));
    breakEval.tick();
    assertEquals(0, count("break", "FINISHED"));
  }

  private long count(String category, String type) {
    return global.latest(1000).stream()
        .filter(n -> type.equals(n.type) && category.equals(n.category))
        .count();
  }
}
