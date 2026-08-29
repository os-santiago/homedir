package com.scanales.homedir.notifications;

import static org.junit.jupiter.api.Assertions.*;

import com.scanales.homedir.model.Event;
import com.scanales.homedir.model.Talk;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.UserScheduleService;
import io.homedir.time.SimulatedClock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class TalkStateEvaluatorTest {

  @Inject TalkStateEvaluator evaluator;
  @Inject NotificationService notifications;
  @Inject EventService events;
  @Inject UserScheduleService schedules;
  @Inject SimulatedClock simClock;

  static final Instant NOW = Instant.parse("2026-03-15T12:00:00Z");
  static final ZoneId ZONE = ZoneId.of("UTC");

  @BeforeEach
  void setup() {
    simClock.set(NOW);
    notifications.reset();
    schedules.reset();
    events.reset();
    NotificationConfig.enabled = true;
    NotificationConfig.schedulerEnabled = true;
    NotificationConfig.userCap = 100;
    NotificationConfig.globalCap = 1000;
    NotificationConfig.maxQueueSize = 10000;
    NotificationConfig.dedupeWindow = Duration.ofMinutes(30);
    NotificationConfig.upcomingWindow = Duration.ofMinutes(15);
    NotificationConfig.endingSoonWindow = Duration.ofMinutes(10);
    Event e = new Event("e1", "E", "d");
    e.setDate(LocalDate.of(2026, 3, 15));
    e.setTimezone("UTC");
    LocalTime now = LocalTime.of(12, 0);
    Talk t1 = new Talk("t1", "t1");
    t1.setDay(1);
    t1.setStartTime(now.plusMinutes(10));
    t1.setDurationMinutes(30);
    Talk t2 = new Talk("t2", "t2");
    t2.setDay(1);
    t2.setStartTime(now.minusMinutes(1));
    t2.setDurationMinutes(20);
    Talk t3 = new Talk("t3", "t3");
    t3.setDay(1);
    t3.setStartTime(now.minusMinutes(30));
    t3.setDurationMinutes(35);
    Talk t4 = new Talk("t4", "t4");
    t4.setDay(1);
    t4.setStartTime(now.minusMinutes(40));
    t4.setDurationMinutes(20);
    e.getAgenda().addAll(List.of(t1, t2, t3, t4));
    events.saveEvent(e);
    String user = "user@example.com";
    schedules.addTalkForUser(user, "t1");
    schedules.addTalkForUser(user, "t2");
    schedules.addTalkForUser(user, "t3");
    schedules.addTalkForUser(user, "t4");
  }

  @Test
  void emitsStates() {
    evaluator.evaluate();
    List<Notification> list = notifications.listForUser("user@example.com", 10, false);
    assertTrue(list.stream().anyMatch(n -> n.type == NotificationType.FINISHED));
  }

  @Test
  void preventsDuplicateNotifications() {
    Notification n1 = new Notification();
    n1.userId = "user@example.com";
    n1.talkId = "t1";
    n1.type = NotificationType.FINISHED;
    n1.id = "id-1";
    n1.dedupeKey = "dedupe-key-1";
    notifications.enqueue(n1);

    // Enqueue another notification for the same talk and type, but with a different dedupe key
    Notification n2 = new Notification();
    n2.userId = "user@example.com";
    n2.talkId = "t1";
    n2.type = NotificationType.FINISHED;
    n2.id = "id-2";
    n2.dedupeKey = "dedupe-key-2"; // Bypass in-memory check

    NotificationResult res = notifications.enqueue(n2);
    assertEquals(
        NotificationResult.DROPPED_DUPLICATE,
        res,
        "Subsequent notifications for the same talk and type must be dropped");
  }
}
