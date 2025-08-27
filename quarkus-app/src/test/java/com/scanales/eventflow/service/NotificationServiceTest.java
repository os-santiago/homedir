package com.scanales.eventflow.service;

import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class NotificationServiceTest {

  @Inject NotificationService notifications;
  @InjectMock CapacityService capacity;

  @BeforeEach
  void setup() {
    notifications.reset();
    when(capacity.evaluate())
        .thenReturn(
            new CapacityService.Status(
                CapacityService.Mode.ADMITTING,
                0,
                0,
                0,
                Instant.now(),
                CapacityService.Trend.STABLE));
  }

  @Test
  public void dedupePreventsDuplicates() {
    NotificationService.Notification n = new NotificationService.Notification();
    n.dedupeKey = "u1-t1-started";
    n.title = "started";
    NotificationService.EnqueueResult r1 = notifications.enqueue("u1", n);
    NotificationService.EnqueueResult r2 = notifications.enqueue("u1", n);
    Assertions.assertEquals(NotificationService.EnqueueResult.ENQUEUED, r1);
    Assertions.assertEquals(NotificationService.EnqueueResult.DUPLICATE, r2);
    Assertions.assertEquals(1, notifications.getForUser("u1").size());
    Map<String, Long> disc = notifications.getDiscarded();
    Assertions.assertEquals(1L, disc.getOrDefault("dedupe", 0L));
  }

  @Test
  public void backpressureSkipsPersistenceButKeepsInMemory() {
    when(capacity.evaluate())
        .thenReturn(
            new CapacityService.Status(
                CapacityService.Mode.CONTAINING,
                0,
                0,
                0,
                Instant.now(),
                CapacityService.Trend.STABLE));
    NotificationService.Notification n = new NotificationService.Notification();
    n.dedupeKey = "u2-t1-upcoming";
    n.title = "upcoming";
    NotificationService.EnqueueResult r = notifications.enqueue("u2", n);
    Assertions.assertEquals(NotificationService.EnqueueResult.BACKPRESSURE, r);
    Assertions.assertEquals(1, notifications.getForUser("u2").size());
    Map<String, Long> disc = notifications.getDiscarded();
    Assertions.assertEquals(1L, disc.getOrDefault("backpressure", 0L));
  }
}
