package com.scanales.eventflow.notifications;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.MultiEmitter;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.logging.Logger;

/** Simple in-memory stream manager for notification SSE connections. */
@ApplicationScoped
public class NotificationStreamService {

  private static final Logger LOG = Logger.getLogger(NotificationStreamService.class);

  @Inject NotificationConfig config;

  private final Map<String, MultiEmitter<? super io.eventflow.notifications.api.NotificationDTO>>
      emitters = new ConcurrentHashMap<>();

  private final Map<String, AtomicInteger> connections = new ConcurrentHashMap<>();

  /**
   * Subscribe the given user to the notification stream.
   *
   * @throws WebApplicationException with status 429 if the user already has an active connection.
   */
  public Multi<io.eventflow.notifications.api.NotificationDTO> subscribe(String userId) {
    AtomicInteger count = connections.computeIfAbsent(userId, k -> new AtomicInteger());
    if (count.incrementAndGet() > config.streamMaxConnectionsPerUser) {
      count.decrementAndGet();
      throw new WebApplicationException(
          Response.status(Response.Status.TOO_MANY_REQUESTS).entity("max connections").build());
    }

    Multi<io.eventflow.notifications.api.NotificationDTO> events =
        Multi.createFrom()
            .<io.eventflow.notifications.api.NotificationDTO>emitter(
                emitter -> {
                  emitters.put(userId, emitter);
                  emitter.onTermination(() -> {
                    emitters.remove(userId);
                    count.decrementAndGet();
                  });
                },
                BackPressureStrategy.BUFFER);

    Multi<io.eventflow.notifications.api.NotificationDTO> heartbeat =
        Multi.createFrom()
            .ticks()
            .every(config.sseHeartbeat)
            .onOverflow().drop()
            .map(
                t -> {
                  io.eventflow.notifications.api.NotificationDTO hb =
                      new io.eventflow.notifications.api.NotificationDTO();
                  hb.type = "HEARTBEAT";
                  hb.createdAt = System.currentTimeMillis();
                  return hb;
                });

    return Multi.createBy().merging().streams(events, heartbeat).runSubscriptionOn(Infrastructure.getDefaultExecutor());
  }

  /** Broadcasts a notification to active subscribers. */
  public void broadcast(Notification n) {
    io.eventflow.notifications.api.NotificationDTO dto = toDTO(n);
    MultiEmitter<? super io.eventflow.notifications.api.NotificationDTO> emitter =
        emitters.get(n.userId);
    if (emitter != null) {
      try {
        emitter.emit(dto);
      } catch (Exception e) {
        LOG.debug("emit failed", e);
      }
    }
  }

  private io.eventflow.notifications.api.NotificationDTO toDTO(Notification n) {
    io.eventflow.notifications.api.NotificationDTO dto =
        new io.eventflow.notifications.api.NotificationDTO();
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
