package io.eventflow.notifications.global;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.StringReader;

/** WebSocket endpoint broadcasting global notifications. */
@ServerEndpoint("/ws/global-notifications")
@ApplicationScoped
public class GlobalNotificationsWs {

  @Inject GlobalNotificationService service;
  @Inject com.scanales.eventflow.service.UsageMetricsService metrics;

  @OnOpen
  public void onOpen(Session session) {
    service.register(session);
    metrics.recordWsHandshake(true);
  }

  @OnClose
  public void onClose(Session session) {
    service.unregister(session);
  }

  @OnMessage
  public void onMessage(String msg, Session session) {
    try {
      JsonObject json = Json.createReader(new StringReader(msg)).readObject();
      if ("hello".equals(json.getString("t", ""))) {
        long cursor = json.getJsonNumber("cursor").longValue();
        // ack
        session.getAsyncRemote().sendText("{" + "\"t\":\"hello-ack\"}");
        service.sendBacklog(session, cursor);
      }
    } catch (Exception e) {
      // ignore malformed messages
    }
  }
}
