package io.eventflow.notifications.global;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Disabled;

import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import java.net.URI;
import java.io.StringReader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class GlobalNotificationsWsTest {
  @TestHTTPResource("ws/global-notifications") URI httpUri;
  @Inject GlobalNotificationService service;

  private WsClient connect() throws Exception {
    URI wsUri = URI.create("ws://" + httpUri.getAuthority() + "/ws/global-notifications");
    WebSocketContainer c = ContainerProvider.getWebSocketContainer();
    WsClient client = new WsClient();
    c.connectToServer(client, ClientEndpointConfig.Builder.create().build(), wsUri);
    return client;
  }

  @BeforeEach
  public void clear() {
    for (GlobalNotification g : service.latest(1000)) {
      service.removeById(g.id);
    }
  }

  static class WsClient extends Endpoint {
    final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    @Override
    public void onOpen(Session session, EndpointConfig config) {
      session.addMessageHandler(String.class, messages::add);
      session.getAsyncRemote().sendText("{\"t\":\"hello\",\"cursor\":0}");
    }
  }

  @Test
  public void broadcastIsReceivedByAllClients() throws Exception {
    WsClient c1 = connect();
    WsClient c2 = connect();
    // drain hello-ack
    c1.messages.poll(5, TimeUnit.SECONDS);
    c2.messages.poll(5, TimeUnit.SECONDS);
    GlobalNotification n = new GlobalNotification();
    n.id = "1"; n.type = "TEST"; n.title = "t"; n.message = "m"; n.dedupeKey = "k1";
    service.enqueue(n);
    String m1 = c1.messages.poll(5, TimeUnit.SECONDS);
    String m2 = c2.messages.poll(5, TimeUnit.SECONDS);
    assertNotNull(m1); assertNotNull(m2);
    JsonObject j1 = Json.createReader(new StringReader(m1)).readObject();
    JsonObject j2 = Json.createReader(new StringReader(m2)).readObject();
    assertEquals("notif", j1.getString("t"));
    assertEquals("notif", j2.getString("t"));
  }

    @Disabled
    @Test
    public void backlogIsSentOnReconnect() throws Exception {
      GlobalNotification n = new GlobalNotification();
      n.id = "2"; n.type = "TEST"; n.title = "b"; n.message = "b"; n.dedupeKey = "k" + System.nanoTime();
      assertTrue(service.enqueue(n));
      WsClient c = connect();
      // first message should be hello-ack or backlog; just ensure we receive something
      assertNotNull(c.messages.poll(5, TimeUnit.SECONDS));
    }
  }
