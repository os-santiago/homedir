package com.scanales.eventflow.notifications;

import static org.junit.jupiter.api.Assertions.*;

import io.eventflow.notifications.api.NotificationDTO;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import com.scanales.eventflow.notifications.Notification;
import com.scanales.eventflow.notifications.NotificationType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NotificationStreamServiceTest {

  @Inject NotificationStreamService service;
  @Inject NotificationConfig config;

  @Test
  void emitsToCorrectUserAndLimitsConnections() {
    config.streamMaxConnectionsPerUser = 1;
    AssertSubscriber<NotificationDTO> sub1 = AssertSubscriber.create(1);
    service.subscribe("u1").subscribe().with(sub1);
    assertThrows(WebApplicationException.class, () -> service.subscribe("u1"));
    AssertSubscriber<NotificationDTO> sub2 = AssertSubscriber.create(1);
    service.subscribe("u2").subscribe().with(sub2);
    Notification n = new Notification();
    n.userId = "u1";
    n.talkId = "t1";
    n.eventId = "e1";
    n.type = NotificationType.STARTED;
    n.title = "t";
    service.broadcast(n);
    sub1.awaitItems(1);
    sub1.assertItems(d -> d.talkId.equals("t1"));
    sub2.assertSubscribed().assertHasNotReceivedAnyItem();
    sub1.cancel();
    sub2.cancel();
  }
}
