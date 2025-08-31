package com.scanales.eventflow.notifications;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NotificationSocketServiceTest {

  @Inject NotificationSocketService service;
  @Inject NotificationConfig config;

  @Test
  void emitsToCorrectUserAndLimitsConnections() {
    config.streamMaxConnectionsPerUser = 1;
    Session s1 = mock(Session.class);
    RemoteEndpoint.Async async1 = mock(RemoteEndpoint.Async.class);
    when(s1.getAsyncRemote()).thenReturn(async1);
    service.register("u1", s1);

    Session s1b = mock(Session.class);
    when(s1b.getAsyncRemote()).thenReturn(mock(RemoteEndpoint.Async.class));
    assertThrows(WebApplicationException.class, () -> service.register("u1", s1b));

    Session s2 = mock(Session.class);
    RemoteEndpoint.Async async2 = mock(RemoteEndpoint.Async.class);
    when(s2.getAsyncRemote()).thenReturn(async2);
    service.register("u2", s2);

    Notification n = new Notification();
    n.userId = "u1";
    n.talkId = "t1";
    n.eventId = "e1";
    n.type = NotificationType.STARTED;
    n.title = "t";
    service.broadcast(n);

    verify(async1).sendText(contains("\"talkId\":\"t1\""));
    verify(async2, never()).sendText(anyString());

    service.unregister("u1", s1);
    service.unregister("u2", s2);
  }
}
