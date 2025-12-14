package com.scanales.eventflow.notifications;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class NotificationServiceTest {

  @Inject
  NotificationService notifications;

  @Test
  public void dedupePreventsDuplicates() {
    notifications.reset();
    NotificationConfig.enabled = true;
    NotificationConfig.maxQueueSize = 10000;
    NotificationConfig.dropOnQueueFull = false;
    NotificationConfig.userCap = 100;
    NotificationConfig.globalCap = 1000;
    NotificationConfig.dedupeWindow = java.time.Duration.ofMinutes(30);
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
    NotificationConfig.enabled = true;
    NotificationConfig.maxQueueSize = 0;
    NotificationConfig.dropOnQueueFull = false;
    NotificationConfig.userCap = 100;
    NotificationConfig.globalCap = 1000;
    NotificationConfig.dedupeWindow = java.time.Duration.ofMinutes(30);
    Notification n = new Notification();
    n.userId = "u2";
    n.talkId = "t1";
    n.type = NotificationType.UPCOMING;
    n.title = "upcoming";
    NotificationResult r = notifications.enqueue(n);
    Assertions.assertEquals(NotificationResult.ACCEPTED_VOLATILE, r);
  }
}
