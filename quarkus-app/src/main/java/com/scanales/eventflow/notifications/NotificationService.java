package com.scanales.eventflow.notifications;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Deque;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jboss.logging.Logger;

/** Central notification service with dedupe and backpressure. */
@ApplicationScoped
public class NotificationService {

  private static final Logger LOG = Logger.getLogger(NotificationService.class);

  @Inject
  NotificationRepository repository;
  @Inject
  NotificationStore store;
  @Inject
  ResourceGuards guards;
  @Inject
  NotificationSocketService socketService;

  private final ConcurrentHashMap<String, Long> dedupe = new ConcurrentHashMap<>();

  private final AtomicLong enqueued = new AtomicLong();
  private final AtomicLong persisted = new AtomicLong();
  private final AtomicLong deduped = new AtomicLong();
  private final AtomicLong dropped = new AtomicLong();
  private final AtomicLong volatileAccepted = new AtomicLong();

  private MessageDigest digest;

  @PostConstruct
  void init() {
    // load existing notifications from disk
    // Not required in this iteration; repository loads lazily per user
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Missing SHA-256 MessageDigest", e);
    }
  }

  /** Enqueues a notification, applying dedupe and capacity checks. */
  public NotificationResult enqueue(Notification n) {
    if (!NotificationConfig.enabled) {
      return NotificationResult.ERROR;
    }
    long now = System.currentTimeMillis();
    n.createdAt = now;
    if (n.id == null) {
      n.id = UUID.randomUUID().toString();
    }
    if (n.dedupeKey == null) {
      n.dedupeKey = NotificationKey.build(n.userId, n.talkId, n.type, now, NotificationConfig.dedupeWindow);
    }
    Long last = dedupe.put(n.dedupeKey, now);
    if (last != null && now - last < NotificationConfig.dedupeWindow.toMillis()) {
      deduped.incrementAndGet();
      log(n, "duplicate");
      return NotificationResult.DROPPED_DUPLICATE;
    }

    Deque<Notification> list = store.getUserList(n.userId);
    if (list.size() >= NotificationConfig.userCap || store.totalSize() >= NotificationConfig.globalCap) {
      dropped.incrementAndGet();
      log(n, "capacity");
      return NotificationResult.DROPPED_CAPACITY;
    }
    list.addLast(n);
    if (list.size() > NotificationConfig.userCap) {
      list.removeFirst();
    }
    enqueued.incrementAndGet();

    NotificationResult result;
    if (guards.checkQueueDepth(repository.queueDepth(), NotificationConfig.maxQueueSize)
        && guards.checkDiskBudget(repository.baseDir(), 10L * 1024 * 1024)) {
      repository.replace(n.userId, list.stream().toList());
      persisted.incrementAndGet();
      log(n, "persisted");
      result = NotificationResult.ACCEPTED_PERSISTED;
    } else if (NotificationConfig.dropOnQueueFull) {
      list.removeLast();
      dropped.incrementAndGet();
      log(n, "drop.queue");
      result = NotificationResult.DROPPED_CAPACITY;
    } else {
      volatileAccepted.incrementAndGet();
      log(n, "volatile");
      result = NotificationResult.ACCEPTED_VOLATILE;
    }
    if (result == NotificationResult.ACCEPTED_PERSISTED
        || result == NotificationResult.ACCEPTED_VOLATILE) {
      socketService.broadcast(n);
    }
    return result;
  }

  /** Lists notifications for a user. */
  public java.util.List<Notification> listForUser(String userId, int limit, boolean onlyUnread) {
    return store.list(userId, limit, onlyUnread);
  }

  /**
   * Paginates notifications for a user applying simple filters.
   *
   * @param userId the owner
   * @param filter one of "all" o "unread"
   * @param cursor timestamp cursor; notifications newer than this are skipped
   * @param limit  max items to return
   */
  public NotificationPage listPage(String userId, String filter, Long cursor, int limit) {
    java.util.Deque<Notification> list = store.getUserList(userId);
    java.util.stream.Stream<Notification> stream = list.stream()
        .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt));
    if ("unread".equalsIgnoreCase(filter)) {
      stream = stream.filter(n -> n.readAt == null);
    }
    if (cursor != null) {
      stream = stream.filter(n -> n.createdAt < cursor);
    }
    java.util.List<Notification> items = stream.limit(limit + 1).toList();
    Long next = null;
    if (items.size() > limit) {
      Notification last = items.get(limit - 1);
      next = last.createdAt;
      items = items.subList(0, limit);
    }
    long unread = list.stream().filter(n -> n.readAt == null).count();
    return new NotificationPage(items, next, unread);
  }

  /** Marks a single notification as read. */
  public boolean markRead(String userId, String id) {
    java.util.Deque<Notification> list = store.getUserList(userId);
    Notification found = list.stream().filter(n -> n.id.equals(id)).findFirst().orElse(null);
    if (found == null)
      return false;
    if (found.readAt == null) {
      found.readAt = System.currentTimeMillis();
      repository.replace(userId, list.stream().toList());
    }
    return true;
  }

  /** Marks all notifications for the user as read. */
  public int markAllRead(String userId) {
    java.util.Deque<Notification> list = store.getUserList(userId);
    long now = System.currentTimeMillis();
    int count = 0;
    for (Notification n : list) {
      if (n.readAt == null) {
        n.readAt = now;
        count++;
      }
    }
    if (count > 0) {
      repository.replace(userId, list.stream().toList());
    }
    return count;
  }

  /** Deletes a notification for the user. */
  public boolean delete(String userId, String id) {
    java.util.Deque<Notification> list = store.getUserList(userId);
    boolean removed = list.removeIf(n -> n.id.equals(id));
    if (removed) {
      repository.replace(userId, list.stream().toList());
    }
    return removed;
  }

  /** Deletes multiple notifications for the user. */
  public int bulkDelete(String userId, java.util.Set<String> ids) {
    java.util.Deque<Notification> list = store.getUserList(userId);
    int before = list.size();
    list.removeIf(n -> ids.contains(n.id));
    int removed = before - list.size();
    if (removed > 0) {
      repository.replace(userId, list.stream().toList());
    }
    return removed;
  }

  /** Counts unread notifications for a user. */
  public long countUnread(String userId) {
    return store.getUserList(userId).stream().filter(n -> n.readAt == null).count();
  }

  /** Page result for notification listings. */
  public record NotificationPage(
      java.util.List<Notification> items, Long nextCursor, long unreadCount) {
  }

  /** Purges notifications older than the retention period. */
  public void purgeOld() {
    long cutoff = Instant.now()
        .minus(NotificationConfig.retentionDays, java.time.temporal.ChronoUnit.DAYS)
        .toEpochMilli();
    store.purgeOlderThan(cutoff);
  }

  /** Testing hook to reset state. */
  public void reset() {
    store.clear();
    dedupe.clear();
  }

  private void log(Notification n, String reason) {
    LOG.infov(
        "enqueue result={0} user_hash={1} talkId={2} type={3} reason={4}",
        n.id, hashUser(n.userId), n.talkId, n.type, reason);
  }

  private String hashUser(String userId) {
    if (userId == null)
      return "";
    byte[] d = digest.digest((NotificationConfig.userHashSalt + userId).getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(d).substring(0, 16);
  }
}
