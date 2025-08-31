package com.scanales.eventflow.notifications;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class NotificationServiceTest {

  @Inject NotificationService notifications;
  @Inject NotificationConfig config;

  @Test
  public void dedupePreventsDuplicates() {
    notifications.reset();
    config.enabled = true;
    config.maxQueueSize = 10000;
    config.dropOnQueueFull = false;
    config.userCap = 100;
    config.globalCap = 1000;
    config.dedupeWindow = java.time.Duration.ofMinutes(30);
    Notification n = new Notification();
    n.userId = "u1";
    n.talkId = "t1";
    n.type = NotificationType.STARTED;
    n.title = "started";
    NotificationResult r1 = notifications.enqueue(n);
    NotificationResult r2 = notifications.enqueue(n);
    Assertions.assertNotEquals(NotificationResult.DROPPED_DUPLICATE, r1);
    Assertions.assertEquals(NotificationResult.DROPPED_DUPLICATE, r2);
    Assertions.assertEquals(1, notifications.listForUser("u1", 10, false).size());
  }

  @Test
  public void queueFullReturnsVolatile() {
    notifications.reset();
    config.enabled = true;
    config.maxQueueSize = 0;
    config.dropOnQueueFull = false;
    config.userCap = 100;
    config.globalCap = 1000;
    config.dedupeWindow = java.time.Duration.ofMinutes(30);
    Notification n = new Notification();
    n.userId = "u2";
    n.talkId = "t1";
    n.type = NotificationType.UPCOMING;
    n.title = "upcoming";
    NotificationResult r = notifications.enqueue(n);
    Assertions.assertEquals(NotificationResult.ACCEPTED_VOLATILE, r);
  }
}
