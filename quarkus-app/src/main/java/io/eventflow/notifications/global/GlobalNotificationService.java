package io.eventflow.notifications.global;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import java.time.Instant;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Service managing global notifications in a ring buffer and broadcasting them to WebSocket clients.
 */
@ApplicationScoped
public class GlobalNotificationService {
  private final Deque<GlobalNotification> buffer = new ConcurrentLinkedDeque<>();
  private final Map<String, Long> dedupe = new ConcurrentHashMap<>();
  private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

  @Inject GlobalNotificationRepository repo;

  public GlobalNotificationService() {}

  @jakarta.annotation.PostConstruct
  void init() {
    buffer.addAll(repo.load());
  }

  public boolean enqueue(GlobalNotification n) {
    if (!GlobalNotificationConfig.enabled) {
      return false;
    }
    n.createdAt = n.createdAt == 0 ? Instant.now().toEpochMilli() : n.createdAt;
    long now = n.createdAt;
    Long last = dedupe.get(n.dedupeKey);
    if (last != null && now - last < GlobalNotificationConfig.dedupeWindow.toMillis()) {
      return false; // deduped
    }
    dedupe.put(n.dedupeKey, now);
    buffer.addLast(n);
    while (buffer.size() > GlobalNotificationConfig.bufferSize) {
      buffer.removeFirst();
    }
    repo.save(buffer);
    broadcast(n);
    return true;
  }

  public void broadcast(GlobalNotification n) {
    String json = Json.message("notif", n);
    for (Session s : sessions) {
      s.getAsyncRemote().sendText(json);
    }
  }

  public void register(Session s) {
    sessions.add(s);
  }

  public void unregister(Session s) {
    sessions.remove(s);
  }

  public void sendBacklog(Session s, long cursor) {
    buffer.stream()
        .filter(n -> n.createdAt > cursor)
        .sorted(Comparator.comparingLong(n -> n.createdAt))
        .forEach(n -> s.getAsyncRemote().sendText(Json.message("notif", n)));
  }

  /** Return the latest N notifications from the buffer (newest first). */
  public java.util.List<GlobalNotification> latest(int limit) {
    return buffer.stream()
        .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt))
        .limit(Math.max(0, limit))
        .toList();
  }

  /** Remove a notification by id from the buffer. */
  public boolean removeById(String id) {
    boolean removed = buffer.removeIf(n -> n.id != null && n.id.equals(id));
    if (removed) {
      repo.save(buffer);
    }
    return removed;
  }
}

/** Simple JSON utility. */
class Json {
  static String message(String t, GlobalNotification n) {
    jakarta.json.JsonObjectBuilder b = jakarta.json.Json.createObjectBuilder();
    b.add("t", t);
    if (n.id != null) b.add("id", n.id);
    if (n.type != null) b.add("type", n.type);
    if (n.category != null) b.add("category", n.category);
    if (n.eventId != null) b.add("eventId", n.eventId);
    if (n.talkId != null) b.add("talkId", n.talkId);
    if (n.title != null) b.add("title", n.title);
    if (n.message != null) b.add("message", n.message);
    b.add("createdAt", n.createdAt);
    if (n.dedupeKey != null) b.add("dedupeKey", n.dedupeKey);
    if (n.expiresAt != null) b.add("expiresAt", n.expiresAt);
    return b.build().toString();
  }
}
