package com.scanales.eventflow.notifications;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jboss.logging.Logger;

/** Central notification service with dedupe and backpressure. */
@ApplicationScoped
public class NotificationService {

  private static final Logger LOG = Logger.getLogger(NotificationService.class);

  @Inject NotificationRepository repository;
  @Inject NotificationStore store;
  @Inject NotificationConfig config;
  @Inject ResourceGuards guards;

  private final ConcurrentHashMap<String, Long> dedupe = new ConcurrentHashMap<>();

  private final AtomicLong enqueued = new AtomicLong();
  private final AtomicLong persisted = new AtomicLong();
  private final AtomicLong deduped = new AtomicLong();
  private final AtomicLong dropped = new AtomicLong();
  private final AtomicLong volatileAccepted = new AtomicLong();

  @PostConstruct
  void init() {
    // load existing notifications from disk
    // Not required in this iteration; repository loads lazily per user
  }

  /** Enqueues a notification, applying dedupe and capacity checks. */
  public NotificationResult enqueue(Notification n) {
    if (!config.enabled) {
      return NotificationResult.ERROR;
    }
    long now = System.currentTimeMillis();
    n.createdAt = now;
    if (n.id == null) {
      n.id = UUID.randomUUID().toString();
    }
    if (n.dedupeKey == null) {
      n.dedupeKey =
          NotificationKey.build(n.userId, n.talkId, n.type, now, config.dedupeWindow);
    }
    Long last = dedupe.put(n.dedupeKey, now);
    if (last != null && now - last < config.dedupeWindow.toMillis()) {
      deduped.incrementAndGet();
      log(n, "duplicate");
      return NotificationResult.DROPPED_DUPLICATE;
    }

    Deque<Notification> list = store.getUserList(n.userId);
    if (list.size() >= config.userCap || store.totalSize() >= config.globalCap) {
      dropped.incrementAndGet();
      log(n, "capacity");
      return NotificationResult.DROPPED_CAPACITY;
    }
    list.addLast(n);
    if (list.size() > config.userCap) {
      list.removeFirst();
    }
    enqueued.incrementAndGet();

    if (guards.checkQueueDepth(repository.queueDepth(), config.maxQueueSize)
        && guards.checkDiskBudget(repository.baseDir(), 10L * 1024 * 1024)) {
      repository.replace(n.userId, list.stream().toList());
      persisted.incrementAndGet();
      log(n, "persisted");
      return NotificationResult.ACCEPTED_PERSISTED;
    }
    if (config.dropOnQueueFull) {
      list.removeLast();
      dropped.incrementAndGet();
      log(n, "drop.queue");
      return NotificationResult.DROPPED_CAPACITY;
    } else {
      volatileAccepted.incrementAndGet();
      log(n, "volatile");
      return NotificationResult.ACCEPTED_VOLATILE;
    }
  }

  /** Lists notifications for a user. */
  public java.util.List<Notification> listForUser(String userId, int limit, boolean onlyUnread) {
    return store.list(userId, limit, onlyUnread);
  }

  /** Purges notifications older than the retention period. */
  public void purgeOld() {
    long cutoff = Instant.now().minus(config.retentionDays, java.time.temporal.ChronoUnit.DAYS).toEpochMilli();
    store.purgeOlderThan(cutoff);
  }

  /** Testing hook to reset state. */
  public void reset() {
    store.clear();
    dedupe.clear();
  }

  private void log(Notification n, String reason) {
    LOG.infov(
        "enqueue result={0} userId={1} talkId={2} type={3} reason={4}",
        n.id,
        n.userId,
        n.talkId,
        n.type,
        reason);
  }
}
