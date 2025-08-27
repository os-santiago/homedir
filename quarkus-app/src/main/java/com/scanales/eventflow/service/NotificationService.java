package com.scanales.eventflow.service;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Central notification service storing user-scoped notifications in memory with asynchronous
 * persistence. Provides basic deduplication and capacity/backpressure guards reused from the user
 * schedule service.
 */
@ApplicationScoped
public class NotificationService {

  private static final Logger LOG = Logger.getLogger(NotificationService.class);

  private final Map<String, List<Notification>> notifications = new ConcurrentHashMap<>();
  private final Map<String, Instant> dedupe = new ConcurrentHashMap<>();
  private final Map<String, LongAdder> discarded = new ConcurrentHashMap<>();

  @ConfigProperty(name = "notifications.dedupe-window", defaultValue = "PT10M")
  Duration dedupeWindow;

  @ConfigProperty(name = "notifications.max-per-user", defaultValue = "500")
  int maxPerUser;

  @Inject PersistenceService persistence;

  @Inject CapacityService capacity;

  @PostConstruct
  void init() {
    notifications.putAll(persistence.loadNotifications());
  }

  /** Enqueue result codes. */
  public enum EnqueueResult {
    ENQUEUED,
    DUPLICATE,
    BACKPRESSURE
  }

  /** Adds a notification for the given user if not recently duplicated. */
  public EnqueueResult enqueue(String userId, Notification n) {
    if (userId == null || n == null || n.dedupeKey == null) {
      return EnqueueResult.DUPLICATE;
    }
    Instant now = Instant.now();
    String key = userId + ':' + n.dedupeKey;
    Instant last = dedupe.put(key, now);
    if (last != null && last.plus(dedupeWindow).isAfter(now)) {
      incrementDiscard("dedupe");
      return EnqueueResult.DUPLICATE;
    }
    n.id = n.id != null ? n.id : java.util.UUID.randomUUID().toString();
    n.createdAt = now;
    notifications
        .computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()))
        .add(n);
    List<Notification> list = notifications.get(userId);
    if (list.size() > maxPerUser) {
      list.remove(0);
    }
    if (canPersist()) {
      persistence.saveNotifications(notifications);
      return EnqueueResult.ENQUEUED;
    } else {
      incrementDiscard("backpressure");
      return EnqueueResult.BACKPRESSURE;
    }
  }

  private boolean canPersist() {
    return capacity.evaluate().mode() != CapacityService.Mode.CONTAINING
        && !persistence.isLowDiskSpace();
  }

  private void incrementDiscard(String reason) {
    discarded.computeIfAbsent(reason, r -> new LongAdder()).increment();
  }

  /** Returns notifications for a user. */
  public List<Notification> getForUser(String userId) {
    return notifications.getOrDefault(userId, List.of());
  }

  /** Snapshot of discard counters. */
  public Map<String, Long> getDiscarded() {
    Map<String, Long> snap = new java.util.HashMap<>();
    discarded.forEach((k, v) -> snap.put(k, v.longValue()));
    return snap;
  }

  /** Clears all stored data (testing only). */
  public void reset() {
    notifications.clear();
    dedupe.clear();
    discarded.clear();
  }

  /** Simple notification model. */
  @RegisterForReflection
  public static class Notification {
    public String id;
    public String userId;
    public String talkId;
    public String eventId;
    public String type;
    public String title;
    public String message;
    public Instant createdAt;
    public Instant readAt;
    public Instant dismissedAt;
    public String dedupeKey;
    public Instant expiresAt;
    public boolean pinned;
  }
}
