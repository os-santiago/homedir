package io.eventflow.notifications;

import com.scanales.eventflow.notifications.NotificationConfig;
import com.scanales.eventflow.notifications.NotificationSocketService;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.jboss.logging.Logger;

/** WebSocket endpoint that streams NotificationDTO events. */
@ServerEndpoint("/api/notifications/ws")
public class NotificationWebSocket {

  private static final Logger LOG = Logger.getLogger(NotificationWebSocket.class);

  @Inject NotificationSocketService sessions;
  @Inject NotificationConfig config;

  @OnOpen
  public void onOpen(Session session) throws java.io.IOException {
    if (!config.wsEnabled) {
      session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "disabled"));
      return;
    }
    String user = null;
    if (session.getUserPrincipal() != null) {
      user = session.getUserPrincipal().getName();
    }
    if (user == null || user.isBlank()) {
      session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized"));
      return;
    }
    session.getUserProperties().put("user", user);
    try {
      sessions.register(user, session);
    } catch (jakarta.ws.rs.WebApplicationException e) {
      session.close(new CloseReason(CloseReason.CloseCodes.TRY_AGAIN_LATER, "max connections"));
    }
  }

  @OnClose
  public void onClose(Session session) {
    Object user = session.getUserProperties().get("user");
    if (user != null) {
      sessions.unregister(user.toString(), session);
    }
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    LOG.debug("ws error", throwable);
  }
}

