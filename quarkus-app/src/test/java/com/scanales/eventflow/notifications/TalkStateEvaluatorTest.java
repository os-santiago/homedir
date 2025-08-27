package com.scanales.eventflow.notifications;

import static org.junit.jupiter.api.Assertions.*;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.UserScheduleService;
import com.scanales.eventflow.notifications.Notification;
import com.scanales.eventflow.notifications.NotificationType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TalkStateEvaluatorTest {

  @Inject TalkStateEvaluator evaluator;
  @Inject NotificationService notifications;
  @Inject EventService events;
  @Inject UserScheduleService schedules;

  @BeforeEach
  void setup() {
    notifications.reset();
    Event e = new Event("e1", "E", "d");
    e.setTimezone("UTC");
    LocalTime now = LocalTime.now();
    Talk t1 = new Talk("t1", "t1");
    t1.setStartTime(now.plusMinutes(10));
    t1.setDurationMinutes(30);
    Talk t2 = new Talk("t2", "t2");
    t2.setStartTime(now.minusMinutes(1));
    t2.setDurationMinutes(20);
    Talk t3 = new Talk("t3", "t3");
    t3.setStartTime(now.minusMinutes(30));
    t3.setDurationMinutes(35);
    Talk t4 = new Talk("t4", "t4");
    t4.setStartTime(now.minusMinutes(30));
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
    assertEquals(4, list.size());
    assertTrue(list.stream().anyMatch(n -> n.type == NotificationType.UPCOMING));
    assertTrue(list.stream().anyMatch(n -> n.type == NotificationType.STARTED));
    assertTrue(list.stream().anyMatch(n -> n.type == NotificationType.ENDING_SOON));
    assertTrue(list.stream().anyMatch(n -> n.type == NotificationType.FINISHED));
  }
}
