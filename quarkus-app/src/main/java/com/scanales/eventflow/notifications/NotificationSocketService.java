package com.scanales.eventflow.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventflow.notifications.api.NotificationDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.logging.Logger;

/** Manages active WebSocket sessions and broadcasts notifications. */
@ApplicationScoped
public class NotificationSocketService {

  private static final Logger LOG = Logger.getLogger(NotificationSocketService.class);

  @Inject NotificationConfig config;
  @Inject ObjectMapper mapper;

  private final ConcurrentMap<String, Set<Session>> sessions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicInteger> connections = new ConcurrentHashMap<>();

  /** Registers a user session enforcing connection limits. */
  public void register(String userId, Session session) {
    AtomicInteger count = connections.computeIfAbsent(userId, k -> new AtomicInteger());
    if (count.incrementAndGet() > config.streamMaxConnectionsPerUser) {
      count.decrementAndGet();
      throw new WebApplicationException(
          Response.status(Response.Status.TOO_MANY_REQUESTS).entity("max connections").build());
    }
    sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
  }

  /** Unregisters a user session. */
  public void unregister(String userId, Session session) {
    Set<Session> userSessions = sessions.get(userId);
    if (userSessions != null) {
      userSessions.remove(session);
      if (userSessions.isEmpty()) {
        sessions.remove(userId);
      }
    }
    AtomicInteger count = connections.get(userId);
    if (count != null) {
      count.decrementAndGet();
    }
  }

  /** Broadcasts a notification to all sessions of the target user. */
  public void broadcast(Notification n) {
    Set<Session> userSessions = sessions.get(n.userId);
    if (userSessions == null || userSessions.isEmpty()) {
      return;
    }
    NotificationDTO dto = toDTO(n);
    String payload;
    try {
      payload = mapper.writeValueAsString(dto);
    } catch (Exception e) {
      LOG.warn("serialize failed", e);
      return;
    }
    for (Session s : userSessions) {
      try {
        s.getAsyncRemote().sendText(payload);
      } catch (Exception e) {
        LOG.debug("send failed", e);
      }
    }
  }

  private NotificationDTO toDTO(Notification n) {
    NotificationDTO dto = new NotificationDTO();
    dto.id = n.id;
    dto.talkId = n.talkId;
    dto.eventId = n.eventId;
    dto.type = n.type != null ? n.type.name() : null;
    dto.title = n.title;
    dto.message = n.message;
    dto.createdAt = n.createdAt;
    return dto;
  }
}
