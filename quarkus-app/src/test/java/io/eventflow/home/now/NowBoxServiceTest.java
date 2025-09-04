package io.eventflow.home.now;

import static org.junit.jupiter.api.Assertions.*;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import io.eventflow.time.SimulatedClock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NowBoxServiceTest {

  @Inject EventService events;
  @Inject NowBoxService service;
  @Inject SimulatedClock sim;

  @BeforeEach
  void setup() {
    events.reset();
    sim.clear();
  }

  @Test
  void selectsLastCurrentNextInEventTimezone() {
    Event e = new Event("e1", "Ev", "d");
    e.setDate(LocalDate.of(2023, 1, 1));
    e.setTimezone("America/Santiago");
    Talk t1 = new Talk("t1", "t1");
    t1.setStartTime(LocalTime.of(9, 0));
    t1.setDurationMinutes(60);
    Talk t2 = new Talk("t2", "t2");
    t2.setStartTime(LocalTime.of(10, 0));
    t2.setDurationMinutes(60);
    Talk t3 = new Talk("t3", "t3");
    t3.setStartTime(LocalTime.of(11, 0));
    t3.setDurationMinutes(60);
    e.getAgenda().addAll(List.of(t1, t2, t3));
    events.saveEvent(e);

    sim.set(Instant.parse("2023-01-01T13:30:00Z")); // 10:30 in Santiago

    NowBoxView view = service.build();
    assertEquals(1, view.events.size());
    NowBoxView.EventNow en = view.events.get(0);
    assertEquals("t1", en.last.id);
    assertEquals("t2", en.current.id);
    assertEquals("t3", en.next.id);
  }
}
