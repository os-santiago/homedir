package com.scanales.eventflow.notifications;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory notification storage per user with capacity limits. */
@ApplicationScoped
public class NotificationStore {
  private final Map<String, Deque<Notification>> data = new ConcurrentHashMap<>();
  private final AtomicInteger total = new AtomicInteger();

  public int totalSize() {
    return total.get();
  }

  public List<Notification> list(String userId, int limit, boolean onlyUnread) {
    Deque<Notification> list = data.get(userId);
    if (list == null) return List.of();
    return list.stream().filter(n -> !onlyUnread || n.readAt == null).limit(limit).toList();
  }

  public Deque<Notification> getUserList(String userId) {
    return data.computeIfAbsent(userId, k -> new ArrayDeque<>());
  }

  public void replace(String userId, List<Notification> list) {
    Deque<Notification> q = new ArrayDeque<>(list);
    data.put(userId, q);
    recomputeTotal();
  }

  public void purgeOlderThan(long cutoff) {
    data.values().forEach(dq -> dq.removeIf(n -> n.createdAt < cutoff));
    recomputeTotal();
  }

  private void recomputeTotal() {
    total.set(data.values().stream().mapToInt(Deque::size).sum());
  }

  /** Clears all stored notifications (testing only). */
  public void clear() {
    data.clear();
    total.set(0);
  }
}
