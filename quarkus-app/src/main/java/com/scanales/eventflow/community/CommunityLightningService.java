package com.scanales.eventflow.community;

import com.scanales.eventflow.notifications.Notification;
import com.scanales.eventflow.notifications.NotificationService;
import com.scanales.eventflow.notifications.NotificationType;
import com.scanales.eventflow.service.PersistenceService;
import com.scanales.eventflow.util.AdminUtils;
import io.eventflow.notifications.global.GlobalNotification;
import io.eventflow.notifications.global.GlobalNotificationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CommunityLightningService {
  private static final Logger LOG = Logger.getLogger(CommunityLightningService.class);
  private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
  public static final String SERVER_LIMIT_MESSAGE =
      "Maximo de publicaciones por minuto del servidor superado, intenta mas tarde";
  private static final String MODE_SHARP_STATEMENT = "sharp_statement";

  @Inject PersistenceService persistenceService;
  @Inject NotificationService notificationService;
  @Inject Instance<GlobalNotificationService> globalNotificationService;
  @Inject CommunityLightningDiscordAlertService discordAlertService;

  @ConfigProperty(name = "community.lightning.publish-interval", defaultValue = "PT1M")
  Duration publishInterval;

  @ConfigProperty(name = "community.lightning.user-post-window", defaultValue = "PT1H")
  Duration userPostWindow;

  @ConfigProperty(name = "community.lightning.server-posts-per-minute", defaultValue = "30")
  int serverPostsPerMinute;

  @ConfigProperty(name = "community.lightning.user-comment-window", defaultValue = "PT1M")
  Duration userCommentWindow;

  @ConfigProperty(name = "community.lightning.server-comments-per-minute", defaultValue = "60")
  int serverCommentsPerMinute;

  @ConfigProperty(name = "community.lightning.queue.max-size", defaultValue = "500")
  int queueMaxSize;

  @ConfigProperty(name = "community.lightning.raid.window", defaultValue = "PT30S")
  Duration raidWindow;

  @ConfigProperty(name = "community.lightning.raid.attempt-threshold", defaultValue = "20")
  int raidAttemptThreshold;

  @ConfigProperty(name = "community.lightning.raid.unique-users-threshold", defaultValue = "8")
  int raidUniqueUsersThreshold;

  @ConfigProperty(name = "community.lightning.raid.cooldown", defaultValue = "PT5M")
  Duration raidCooldown;

  @ConfigProperty(name = "community.lightning.max-title-length", defaultValue = "100")
  int maxTitleLength;

  @ConfigProperty(name = "community.lightning.max-body-length", defaultValue = "100")
  int maxBodyLength;

  @ConfigProperty(name = "community.lightning.max-comment-length", defaultValue = "200")
  int maxCommentLength;

  private final Object stateLock = new Object();
  private final LinkedHashMap<String, CommunityLightningThread> threads = new LinkedHashMap<>();
  private final LinkedHashMap<String, CommunityLightningComment> comments = new LinkedHashMap<>();
  private final LinkedHashMap<String, String> threadLikesByUser = new LinkedHashMap<>();
  private final LinkedHashMap<String, String> commentLikesByUser = new LinkedHashMap<>();
  private final LinkedHashMap<String, CommunityLightningReport> reports = new LinkedHashMap<>();
  private final LinkedHashMap<String, String> reportIndexByUserTarget = new LinkedHashMap<>();
  private final ArrayDeque<QueuedThread> publishQueue = new ArrayDeque<>();
  private final ArrayDeque<AttemptEntry> postWindowAttempts = new ArrayDeque<>();
  private final ArrayDeque<AttemptEntry> commentWindowAttempts = new ArrayDeque<>();
  private final ArrayDeque<AttemptEntry> raidWindowAttempts = new ArrayDeque<>();
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "community-lightning-queue");
            thread.setDaemon(true);
            return thread;
          });

  private volatile Instant nextPublishAt = Instant.EPOCH;
  private volatile Instant raidCooldownUntil = Instant.EPOCH;
  private volatile long lastKnownMtime = Long.MIN_VALUE;

  @PostConstruct
  void init() {
    synchronized (stateLock) {
      refreshFromDisk(true);
      rebuildQueueFromSnapshot();
    }
    scheduler.scheduleWithFixedDelay(this::drainQueueSafe, 1L, 1L, TimeUnit.SECONDS);
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }

  public FeedPage listPublished(int limit, int offset) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      int safeLimit = normalizeLimit(limit, 10, 30);
      int safeOffset = Math.max(0, offset);
      List<CommunityLightningThread> ordered =
          threads.values().stream()
              .filter(thread -> thread.publishedAt() != null)
              .sorted(
                  Comparator.comparing(
                          CommunityLightningThread::publishedAt,
                          Comparator.nullsLast(Comparator.reverseOrder()))
                      .thenComparing(
                          CommunityLightningThread::createdAt,
                          Comparator.nullsLast(Comparator.reverseOrder())))
              .toList();
      if (safeOffset >= ordered.size()) {
        return new FeedPage(
            List.of(), ordered.size(), safeLimit, safeOffset, publishQueue.size(), nextPublishAt);
      }
      int end = Math.min(ordered.size(), safeOffset + safeLimit);
      return new FeedPage(
          ordered.subList(safeOffset, end),
          ordered.size(),
          safeLimit,
          safeOffset,
          publishQueue.size(),
          nextPublishAt);
    }
  }

  public Map<String, List<CommunityLightningComment>> listCommentsForThreads(
      List<String> threadIds, int perThreadLimit) {
    if (threadIds == null || threadIds.isEmpty()) {
      return Map.of();
    }
    synchronized (stateLock) {
      refreshFromDisk(false);
      int safeLimit = normalizeLimit(perThreadLimit, 3, 10);
      LinkedHashMap<String, List<CommunityLightningComment>> out = new LinkedHashMap<>();
      for (String threadId : threadIds) {
        if (threadId == null || threadId.isBlank()) {
          continue;
        }
        List<CommunityLightningComment> perThread =
            comments.values().stream()
                .filter(comment -> threadId.equals(comment.threadId()))
                .sorted(
                    Comparator.comparingInt(CommunityLightningComment::likes)
                        .reversed()
                        .thenComparing(
                            CommunityLightningComment::createdAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(safeLimit)
                .toList();
        out.put(threadId, perThread);
      }
      return out;
    }
  }

  public Map<String, Instant> lastCommentAtByThread(List<String> threadIds) {
    if (threadIds == null || threadIds.isEmpty()) {
      return Map.of();
    }
    synchronized (stateLock) {
      refreshFromDisk(false);
      LinkedHashMap<String, Instant> out = new LinkedHashMap<>();
      for (String threadId : threadIds) {
        if (threadId == null || threadId.isBlank()) {
          continue;
        }
        Instant last =
            comments.values().stream()
                .filter(comment -> threadId.equals(comment.threadId()))
                .map(
                    comment ->
                        comment.updatedAt() != null ? comment.updatedAt() : comment.createdAt())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        out.put(threadId, last);
      }
      return out;
    }
  }

  public Optional<CommunityLightningThread> findThread(String threadId) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      if (threadId == null || threadId.isBlank()) {
        return Optional.empty();
      }
      return Optional.ofNullable(threads.get(threadId));
    }
  }

  public CreateThreadResult createThread(String userId, String userName, CreateThreadRequest request) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedUserId == null) {
        throw new ValidationException("user_id_required");
      }
      String safeMode = sanitizeMode(request != null ? request.mode() : null);
      if (safeMode == null) {
        throw new ValidationException("invalid_mode");
      }
      String safeTitle = sanitizeText(request != null ? request.title() : null, maxTitleLength);
      if (safeTitle == null) {
        safeTitle = sanitizeText(request != null ? request.body() : null, maxTitleLength);
      }
      if (safeTitle == null) {
        throw new ValidationException("invalid_title");
      }
      String safeBody = sanitizeText(request != null ? request.body() : null, maxBodyLength);
      if (safeBody == null) {
        safeBody = safeTitle;
      }

      Instant now = Instant.now();
      if (isRaidCooldownActive(now)) {
        throw new RateLimitExceededException("raid_cooldown", SERVER_LIMIT_MESSAGE);
      }
      enforceServerPostLimit(now);
      registerRaidAttempt(normalizedUserId, now);
      enforceUserWindow(normalizedUserId, now);

      if (queueMaxSize > 0 && publishQueue.size() >= queueMaxSize) {
        throw new RateLimitExceededException("queue_full", SERVER_LIMIT_MESSAGE);
      }

      String id = UUID.randomUUID().toString();
      CommunityLightningThread baseThread =
          new CommunityLightningThread(
              id,
              safeMode,
              safeTitle,
              safeBody,
              normalizedUserId,
              sanitizeUserName(userName),
              now,
              now,
              null,
              null,
              0,
              0,
              0);
      boolean queued = true;
      int queuePosition;
      if (canPublishImmediately(now)) {
        queued = false;
        queuePosition = 0;
        threads.put(id, publishThread(baseThread, now));
      } else {
        threads.put(id, baseThread);
        publishQueue.addLast(new QueuedThread(id, now));
        queuePosition = publishQueue.size();
      }
      persistSync();
      return new CreateThreadResult(threads.get(id), queued, queuePosition, nextPublishAt);
    }
  }

  public CommentResult addComment(String userId, String userName, String threadId, String body) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedUserId == null) {
        throw new ValidationException("user_id_required");
      }
      CommunityLightningThread thread = findThreadOrThrow(threadId);
      if (thread.publishedAt() == null) {
        throw new ValidationException("thread_not_published");
      }
      String safeBody = sanitizeText(body, maxCommentLength);
      if (safeBody == null) {
        throw new ValidationException("invalid_comment");
      }

      Instant now = Instant.now();
      if (isRaidCooldownActive(now)) {
        throw new RateLimitExceededException("raid_cooldown", SERVER_LIMIT_MESSAGE);
      }
      enforceServerCommentLimit(now);
      registerRaidAttempt(normalizedUserId, now);
      enforceUserCommentWindow(normalizedUserId, now);

      String commentId = UUID.randomUUID().toString();
      CommunityLightningComment comment =
          new CommunityLightningComment(
              commentId,
              thread.id(),
              safeBody,
              normalizedUserId,
              sanitizeUserName(userName),
              now,
              now,
              0,
              0);
      comments.put(commentId, comment);
      CommunityLightningThread updatedThread =
          new CommunityLightningThread(
              thread.id(),
              thread.mode(),
              thread.title(),
              thread.body(),
              thread.userId(),
              thread.userName(),
              thread.createdAt(),
              now,
              thread.publishedAt(),
              thread.bestCommentId(),
              thread.likes(),
              thread.comments() + 1,
              thread.reports());
      threads.put(thread.id(), updatedThread);
      recomputeBestComment(thread.id(), now);
      persistSync();
      return new CommentResult(threads.get(thread.id()), comment);
    }
  }

  public CommunityLightningThread editThread(String userId, String threadId, String statement) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedUserId == null) {
        throw new ValidationException("user_id_required");
      }
      CommunityLightningThread thread = findThreadOrThrow(threadId);
      if (!normalizedUserId.equals(thread.userId())) {
        throw new ForbiddenException("thread_edit_forbidden");
      }
      String safeStatement = sanitizeText(statement, maxTitleLength);
      if (safeStatement == null) {
        throw new ValidationException("invalid_title");
      }
      Instant now = Instant.now();
      CommunityLightningThread updated =
          new CommunityLightningThread(
              thread.id(),
              thread.mode(),
              safeStatement,
              safeStatement,
              thread.userId(),
              thread.userName(),
              thread.createdAt(),
              now,
              thread.publishedAt(),
              thread.bestCommentId(),
              thread.likes(),
              thread.comments(),
              thread.reports());
      threads.put(thread.id(), updated);
      persistSync();
      return updated;
    }
  }

  public CommentResult editComment(String userId, String commentId, String body) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedUserId == null) {
        throw new ValidationException("user_id_required");
      }
      CommunityLightningComment comment = findCommentOrThrow(commentId);
      if (!normalizedUserId.equals(comment.userId())) {
        throw new ForbiddenException("comment_edit_forbidden");
      }
      String safeBody = sanitizeText(body, maxCommentLength);
      if (safeBody == null) {
        throw new ValidationException("invalid_comment");
      }
      Instant now = Instant.now();
      CommunityLightningComment updatedComment =
          new CommunityLightningComment(
              comment.id(),
              comment.threadId(),
              safeBody,
              comment.userId(),
              comment.userName(),
              comment.createdAt(),
              now,
              comment.likes(),
              comment.reports());
      comments.put(comment.id(), updatedComment);
      CommunityLightningThread thread = threads.get(comment.threadId());
      if (thread != null) {
        threads.put(
            thread.id(),
            new CommunityLightningThread(
                thread.id(),
                thread.mode(),
                thread.title(),
                thread.body(),
                thread.userId(),
                thread.userName(),
                thread.createdAt(),
                now,
                thread.publishedAt(),
                thread.bestCommentId(),
                thread.likes(),
                thread.comments(),
                thread.reports()));
      }
      persistSync();
      return new CommentResult(threads.get(comment.threadId()), updatedComment);
    }
  }

  public LikeResult setThreadLiked(String userId, String threadId, boolean liked) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedUserId == null) {
        throw new ValidationException("user_id_required");
      }
      CommunityLightningThread thread = findThreadOrThrow(threadId);
      String key = normalizedUserId + "|" + thread.id();
      boolean alreadyLiked = threadLikesByUser.containsKey(key);
      if (liked && !alreadyLiked) {
        threadLikesByUser.put(key, thread.id());
        thread =
            new CommunityLightningThread(
                thread.id(),
                thread.mode(),
                thread.title(),
                thread.body(),
                thread.userId(),
                thread.userName(),
                thread.createdAt(),
                Instant.now(),
                thread.publishedAt(),
                thread.bestCommentId(),
                thread.likes() + 1,
                thread.comments(),
                thread.reports());
        threads.put(thread.id(), thread);
        persistSync();
      } else if (!liked && alreadyLiked) {
        threadLikesByUser.remove(key);
        int nextLikes = Math.max(0, thread.likes() - 1);
        thread =
            new CommunityLightningThread(
                thread.id(),
                thread.mode(),
                thread.title(),
                thread.body(),
                thread.userId(),
                thread.userName(),
                thread.createdAt(),
                Instant.now(),
                thread.publishedAt(),
                thread.bestCommentId(),
                nextLikes,
                thread.comments(),
                thread.reports());
        threads.put(thread.id(), thread);
        persistSync();
      }
      return new LikeResult(thread, null, liked);
    }
  }

  public LikeResult setCommentLiked(String userId, String commentId, boolean liked) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedUserId == null) {
        throw new ValidationException("user_id_required");
      }
      CommunityLightningComment comment = findCommentOrThrow(commentId);
      String key = normalizedUserId + "|" + comment.id();
      boolean alreadyLiked = commentLikesByUser.containsKey(key);
      if (liked && !alreadyLiked) {
        commentLikesByUser.put(key, comment.id());
        comment =
            new CommunityLightningComment(
                comment.id(),
                comment.threadId(),
                comment.body(),
                comment.userId(),
                comment.userName(),
                comment.createdAt(),
                Instant.now(),
                comment.likes() + 1,
                comment.reports());
        comments.put(comment.id(), comment);
        recomputeBestComment(comment.threadId(), Instant.now());
        persistSync();
      } else if (!liked && alreadyLiked) {
        commentLikesByUser.remove(key);
        comment =
            new CommunityLightningComment(
                comment.id(),
                comment.threadId(),
                comment.body(),
                comment.userId(),
                comment.userName(),
                comment.createdAt(),
                Instant.now(),
                Math.max(0, comment.likes() - 1),
                comment.reports());
        comments.put(comment.id(), comment);
        recomputeBestComment(comment.threadId(), Instant.now());
        persistSync();
      }
      CommunityLightningThread thread = threads.get(comment.threadId());
      return new LikeResult(thread, comment, liked);
    }
  }

  public ReportResult reportThread(String userId, String userName, String threadId, String reason) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedUserId == null) {
        throw new ValidationException("user_id_required");
      }
      CommunityLightningThread thread = findThreadOrThrow(threadId);
      String safeReason = sanitizeText(reason, 180);
      if (safeReason == null) {
        throw new ValidationException("invalid_reason");
      }
      return reportTarget(
          normalizedUserId, sanitizeUserName(userName), "thread", thread.id(), thread.id(), safeReason);
    }
  }

  public ReportResult reportComment(String userId, String userName, String commentId, String reason) {
    synchronized (stateLock) {
      refreshFromDisk(false);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedUserId == null) {
        throw new ValidationException("user_id_required");
      }
      CommunityLightningComment comment = findCommentOrThrow(commentId);
      String safeReason = sanitizeText(reason, 180);
      if (safeReason == null) {
        throw new ValidationException("invalid_reason");
      }
      return reportTarget(
          normalizedUserId,
          sanitizeUserName(userName),
          "comment",
          comment.id(),
          comment.threadId(),
          safeReason);
    }
  }

  public Stats stats() {
    synchronized (stateLock) {
      refreshFromDisk(false);
      long published =
          threads.values().stream().filter(thread -> thread.publishedAt() != null).count();
      return new Stats(
          threads.size(),
          published,
          comments.size(),
          reports.size(),
          publishQueue.size(),
          nextPublishAt,
          raidCooldownUntil);
    }
  }

  public void clearAllForTests() {
    synchronized (stateLock) {
      threads.clear();
      comments.clear();
      threadLikesByUser.clear();
      commentLikesByUser.clear();
      reports.clear();
      reportIndexByUserTarget.clear();
      publishQueue.clear();
      postWindowAttempts.clear();
      commentWindowAttempts.clear();
      raidWindowAttempts.clear();
      nextPublishAt = Instant.EPOCH;
      raidCooldownUntil = Instant.EPOCH;
      persistSync();
    }
  }

  private ReportResult reportTarget(
      String userId,
      String userName,
      String targetType,
      String targetId,
      String threadId,
      String reason) {
    String dedupeKey = userId + "|" + targetType + "|" + targetId;
    String existing = reportIndexByUserTarget.get(dedupeKey);
    if (existing != null) {
      CommunityLightningReport report = reports.get(existing);
      int count = targetReportCount(targetType, targetId);
      return new ReportResult(existing, true, report, count);
    }
    Instant now = Instant.now();
    String reportId = UUID.randomUUID().toString();
    CommunityLightningReport report =
        new CommunityLightningReport(
            reportId,
            targetType,
            targetId,
            threadId,
            userId,
            userName,
            reason,
            now);
    reports.put(reportId, report);
    reportIndexByUserTarget.put(dedupeKey, reportId);
    incrementReportCounter(targetType, targetId, now);
    persistSync();
    notifyModeration(report);
    int count = targetReportCount(targetType, targetId);
    return new ReportResult(reportId, false, report, count);
  }

  private void incrementReportCounter(String targetType, String targetId, Instant now) {
    if ("thread".equals(targetType)) {
      CommunityLightningThread thread = threads.get(targetId);
      if (thread != null) {
        threads.put(
            thread.id(),
            new CommunityLightningThread(
                thread.id(),
                thread.mode(),
                thread.title(),
                thread.body(),
                thread.userId(),
                thread.userName(),
                thread.createdAt(),
                now,
                thread.publishedAt(),
                thread.bestCommentId(),
                thread.likes(),
                thread.comments(),
                thread.reports() + 1));
      }
      return;
    }
    CommunityLightningComment comment = comments.get(targetId);
    if (comment != null) {
      comments.put(
          comment.id(),
          new CommunityLightningComment(
              comment.id(),
              comment.threadId(),
              comment.body(),
              comment.userId(),
              comment.userName(),
              comment.createdAt(),
              now,
              comment.likes(),
              comment.reports() + 1));
    }
  }

  private int targetReportCount(String targetType, String targetId) {
    if ("thread".equals(targetType)) {
      CommunityLightningThread thread = threads.get(targetId);
      return thread == null ? 0 : thread.reports();
    }
    CommunityLightningComment comment = comments.get(targetId);
    return comment == null ? 0 : comment.reports();
  }

  private void notifyModeration(CommunityLightningReport report) {
    String summary =
        "Community report on " + safe(report.targetType()) + " " + safe(report.targetId()) + ".";
    for (String adminUser : AdminUtils.getAdminList()) {
      if (adminUser == null || adminUser.isBlank()) {
        continue;
      }
      try {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID().toString();
        notification.userId = adminUser.trim().toLowerCase(Locale.ROOT);
        notification.type = NotificationType.SOCIAL;
        notification.title = "Community moderation report";
        notification.message = summary + " Reason: " + safe(report.reason());
        notification.talkId = report.targetId();
        notification.eventId = report.threadId();
        notification.dedupeKey = "community-report:" + report.id() + ":" + adminUser;
        notificationService.enqueue(notification);
      } catch (Exception e) {
        LOG.debugf(e, "community_lightning_admin_notify_failed user=%s", adminUser);
      }
    }
    emitGlobalReportAlert(report, summary);
    discordAlertService.sendReportAlertAsync(report, summary);
  }

  private void emitGlobalReportAlert(CommunityLightningReport report, String summary) {
    if (!globalNotificationService.isResolvable()) {
      return;
    }
    try {
      GlobalNotification alert = new GlobalNotification();
      alert.id = UUID.randomUUID().toString();
      alert.type = "COMMUNITY_REPORT";
      alert.category = "social";
      alert.title = "Community moderation report";
      alert.message = summary;
      alert.targetUrl = "/comunidad/moderation";
      alert.createdAt = Instant.now().toEpochMilli();
      alert.dedupeKey =
          "community-report:"
              + safe(report.targetType())
              + ":"
              + safe(report.targetId())
              + ":"
              + report.createdAt().truncatedTo(ChronoUnit.MINUTES);
      globalNotificationService.get().enqueue(alert);
    } catch (Exception e) {
      LOG.debug("community_lightning_global_report_alert_failed", e);
    }
  }

  private void recomputeBestComment(String threadId, Instant now) {
    CommunityLightningThread thread = threads.get(threadId);
    if (thread == null) {
      return;
    }
    List<CommunityLightningComment> candidates =
        comments.values().stream()
            .filter(comment -> threadId.equals(comment.threadId()))
            .sorted(
                Comparator.comparingInt(CommunityLightningComment::likes)
                    .reversed()
                    .thenComparing(
                        CommunityLightningComment::createdAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    String bestId = candidates.isEmpty() ? null : candidates.getFirst().id();
    if (Objects.equals(bestId, thread.bestCommentId())) {
      return;
    }
    CommunityLightningThread updated =
        new CommunityLightningThread(
            thread.id(),
            thread.mode(),
            thread.title(),
            thread.body(),
            thread.userId(),
            thread.userName(),
            thread.createdAt(),
            now,
            thread.publishedAt(),
            bestId,
            thread.likes(),
            thread.comments(),
            thread.reports());
    threads.put(thread.id(), updated);
    if (bestId != null) {
      CommunityLightningComment best = comments.get(bestId);
      if (best != null) {
        emitBestAnswerUpdate(updated, best);
      }
    }
  }

  private void emitBestAnswerUpdate(
      CommunityLightningThread thread, CommunityLightningComment bestComment) {
    if (bestComment.userId() != null && !bestComment.userId().isBlank()) {
      try {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID().toString();
        notification.userId = bestComment.userId();
        notification.type = NotificationType.SOCIAL;
        notification.title = "Best answer spotlight";
        notification.message = "Your answer is now highlighted in Lightning Threads.";
        notification.eventId = thread.id();
        notification.talkId = bestComment.id();
        notification.dedupeKey = "community-best-answer:" + thread.id() + ":" + bestComment.id();
        notificationService.enqueue(notification);
      } catch (Exception e) {
        LOG.debug("community_lightning_best_answer_notify_failed", e);
      }
    }
    if (!globalNotificationService.isResolvable()) {
      return;
    }
    try {
      GlobalNotification alert = new GlobalNotification();
      alert.id = UUID.randomUUID().toString();
      alert.type = "LIGHTNING_BEST_ANSWER";
      alert.category = "social";
      alert.title = "New best answer in Lightning Threads";
      alert.message = safe(bestComment.userName()) + " is leading: " + safe(thread.title());
      alert.targetUrl = "/";
      alert.createdAt = Instant.now().toEpochMilli();
      alert.dedupeKey = "community-best-answer:" + thread.id() + ":" + bestComment.id();
      globalNotificationService.get().enqueue(alert);
    } catch (Exception e) {
      LOG.debug("community_lightning_best_answer_global_failed", e);
    }
  }

  private void enforceServerPostLimit(Instant now) {
    if (serverPostsPerMinute <= 0) {
      return;
    }
    Instant floor = now.minus(1, ChronoUnit.MINUTES);
    trimAttempts(postWindowAttempts, floor);
    if (postWindowAttempts.size() >= serverPostsPerMinute) {
      throw new RateLimitExceededException("server_post_minute_limit_reached", SERVER_LIMIT_MESSAGE);
    }
    postWindowAttempts.addLast(new AttemptEntry("server", now));
  }

  private void enforceServerCommentLimit(Instant now) {
    if (serverCommentsPerMinute <= 0) {
      return;
    }
    Instant floor = now.minus(1, ChronoUnit.MINUTES);
    trimAttempts(commentWindowAttempts, floor);
    if (commentWindowAttempts.size() >= serverCommentsPerMinute) {
      throw new RateLimitExceededException("server_comment_minute_limit_reached", SERVER_LIMIT_MESSAGE);
    }
    commentWindowAttempts.addLast(new AttemptEntry("server", now));
  }

  private void registerRaidAttempt(String userId, Instant now) {
    Instant floor = now.minus(raidWindow != null ? raidWindow : Duration.ofSeconds(30));
    trimAttempts(raidWindowAttempts, floor);
    raidWindowAttempts.addLast(new AttemptEntry(userId, now));
    if (raidAttemptThreshold <= 0 || raidUniqueUsersThreshold <= 0) {
      return;
    }
    if (raidWindowAttempts.size() < raidAttemptThreshold) {
      return;
    }
    long uniqueUsers =
        raidWindowAttempts.stream().map(AttemptEntry::userId).distinct().count();
    if (uniqueUsers < raidUniqueUsersThreshold) {
      return;
    }
    raidCooldownUntil = now.plus(raidCooldown != null ? raidCooldown : Duration.ofMinutes(5));
    emitRaidAlert(uniqueUsers, raidWindowAttempts.size());
    throw new RateLimitExceededException("raid_detected", SERVER_LIMIT_MESSAGE);
  }

  private void emitRaidAlert(long uniqueUsers, int attempts) {
    LOG.warnf(
        "community_lightning_raid_detected unique_users=%d attempts=%d cooldown_until=%s",
        uniqueUsers,
        attempts,
        raidCooldownUntil);
    if (!globalNotificationService.isResolvable()) {
      return;
    }
    try {
      GlobalNotification alert = new GlobalNotification();
      alert.id = UUID.randomUUID().toString();
      alert.type = "COMMUNITY_RAID_GUARD";
      alert.category = "social";
      alert.title = "Community raid guard enabled";
      alert.message = "Rate guard is protecting Lightning Threads due to unusual post bursts.";
      alert.targetUrl = "/private/admin/metrics";
      alert.createdAt = Instant.now().toEpochMilli();
      alert.dedupeKey =
          "community-raid:" + Instant.now().truncatedTo(ChronoUnit.MINUTES).toString();
      globalNotificationService.get().enqueue(alert);
    } catch (Exception e) {
      LOG.debug("community_lightning_raid_alert_failed", e);
    }
  }

  private boolean isRaidCooldownActive(Instant now) {
    return raidCooldownUntil != null && now.isBefore(raidCooldownUntil);
  }

  private void enforceUserWindow(String userId, Instant now) {
    Duration window = userPostWindow != null ? userPostWindow : Duration.ofHours(1);
    Instant floor = now.minus(window);
    boolean violated =
        threads.values().stream()
            .filter(thread -> userId.equals(thread.userId()))
            .filter(thread -> thread.createdAt() != null && !thread.createdAt().isBefore(floor))
            .findAny()
            .isPresent();
    if (violated) {
      throw new RateLimitExceededException("user_hourly_post_limit", "You can post once per hour.");
    }
  }

  private void enforceUserCommentWindow(String userId, Instant now) {
    Duration window = userCommentWindow != null ? userCommentWindow : Duration.ofMinutes(1);
    Instant floor = now.minus(window);
    boolean violated =
        comments.values().stream()
            .filter(comment -> userId.equals(comment.userId()))
            .filter(comment -> comment.createdAt() != null && !comment.createdAt().isBefore(floor))
            .findAny()
            .isPresent();
    if (violated) {
      throw new RateLimitExceededException("user_comment_rate_limit", "You can reply once per minute.");
    }
  }

  private CommunityLightningThread publishThread(CommunityLightningThread thread, Instant now) {
    Instant publishAt = now != null ? now : Instant.now();
    nextPublishAt = publishAt.plus(publishInterval != null ? publishInterval : Duration.ofMinutes(1));
    return new CommunityLightningThread(
        thread.id(),
        thread.mode(),
        thread.title(),
        thread.body(),
        thread.userId(),
        thread.userName(),
        thread.createdAt(),
        publishAt,
        publishAt,
        thread.bestCommentId(),
        thread.likes(),
        thread.comments(),
        thread.reports());
  }

  private boolean canPublishImmediately(Instant now) {
    if (!publishQueue.isEmpty()) {
      return false;
    }
    return nextPublishAt == null || !now.isBefore(nextPublishAt);
  }

  private void drainQueueSafe() {
    try {
      drainQueue();
    } catch (Exception e) {
      LOG.debug("community_lightning_queue_drain_failed", e);
    }
  }

  private void drainQueue() {
    synchronized (stateLock) {
      refreshFromDisk(false);
      if (publishQueue.isEmpty()) {
        return;
      }
      boolean changed = false;
      Instant now = Instant.now();
      while (!publishQueue.isEmpty() && (nextPublishAt == null || !now.isBefore(nextPublishAt))) {
        QueuedThread queued = publishQueue.pollFirst();
        if (queued == null || queued.threadId() == null) {
          continue;
        }
        CommunityLightningThread thread = threads.get(queued.threadId());
        if (thread == null || thread.publishedAt() != null) {
          continue;
        }
        CommunityLightningThread published = publishThread(thread, now);
        threads.put(published.id(), published);
        changed = true;
        now = Instant.now();
      }
      if (changed) {
        persistSync();
      }
    }
  }

  private void persistSync() {
    try {
      persistenceService.saveCommunityLightningStateSync(toSnapshot());
      lastKnownMtime = persistenceService.communityLightningStateLastModifiedMillis();
    } catch (IllegalStateException e) {
      throw new IllegalStateException("failed_to_persist_lightning_state", e);
    }
  }

  private void refreshFromDisk(boolean force) {
    long mtime = persistenceService.communityLightningStateLastModifiedMillis();
    if (!force && mtime == lastKnownMtime) {
      return;
    }
    Optional<CommunityLightningStateSnapshot> loaded = persistenceService.loadCommunityLightningState();
    CommunityLightningStateSnapshot snapshot = loaded.orElseGet(CommunityLightningStateSnapshot::empty);
    threads.clear();
    comments.clear();
    threadLikesByUser.clear();
    commentLikesByUser.clear();
    reports.clear();
    reportIndexByUserTarget.clear();
    if (snapshot.threads() != null) {
      threads.putAll(snapshot.threads());
    }
    if (snapshot.comments() != null) {
      comments.putAll(snapshot.comments());
    }
    if (snapshot.threadLikesByUser() != null) {
      threadLikesByUser.putAll(snapshot.threadLikesByUser());
    }
    if (snapshot.commentLikesByUser() != null) {
      commentLikesByUser.putAll(snapshot.commentLikesByUser());
    }
    if (snapshot.reports() != null) {
      reports.putAll(snapshot.reports());
    }
    if (snapshot.reportIndexByUserTarget() != null) {
      reportIndexByUserTarget.putAll(snapshot.reportIndexByUserTarget());
    }
    rebuildQueueFromSnapshot();
    lastKnownMtime = mtime;
  }

  private void rebuildQueueFromSnapshot() {
    publishQueue.clear();
    List<CommunityLightningThread> unpublished =
        threads.values().stream()
            .filter(thread -> thread.publishedAt() == null)
            .sorted(
                Comparator.comparing(
                    CommunityLightningThread::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    for (CommunityLightningThread thread : unpublished) {
      publishQueue.addLast(new QueuedThread(thread.id(), thread.createdAt()));
    }
    if (publishQueue.isEmpty()) {
      nextPublishAt = Instant.EPOCH;
    } else if (nextPublishAt == null || nextPublishAt.equals(Instant.EPOCH)) {
      nextPublishAt = Instant.now();
    }
  }

  private CommunityLightningStateSnapshot toSnapshot() {
    return new CommunityLightningStateSnapshot(
        CommunityLightningStateSnapshot.CURRENT_SCHEMA_VERSION,
        new LinkedHashMap<>(threads),
        new LinkedHashMap<>(comments),
        new LinkedHashMap<>(threadLikesByUser),
        new LinkedHashMap<>(commentLikesByUser),
        new LinkedHashMap<>(reports),
        new LinkedHashMap<>(reportIndexByUserTarget));
  }

  private CommunityLightningThread findThreadOrThrow(String threadId) {
    if (threadId == null || threadId.isBlank()) {
      throw new ValidationException("thread_id_required");
    }
    CommunityLightningThread thread = threads.get(threadId);
    if (thread == null) {
      throw new NotFoundException("thread_not_found");
    }
    return thread;
  }

  private CommunityLightningComment findCommentOrThrow(String commentId) {
    if (commentId == null || commentId.isBlank()) {
      throw new ValidationException("comment_id_required");
    }
    CommunityLightningComment comment = comments.get(commentId);
    if (comment == null) {
      throw new NotFoundException("comment_not_found");
    }
    return comment;
  }

  private static int normalizeLimit(int raw, int fallback, int max) {
    if (raw <= 0) {
      return fallback;
    }
    return Math.min(raw, max);
  }

  private static void trimAttempts(ArrayDeque<AttemptEntry> attempts, Instant floor) {
    while (!attempts.isEmpty()) {
      AttemptEntry first = attempts.peekFirst();
      if (first == null || first.at() == null || !first.at().isBefore(floor)) {
        break;
      }
      attempts.pollFirst();
    }
  }

  private String sanitizeMode(String mode) {
    return MODE_SHARP_STATEMENT;
  }

  private String sanitizeUserId(String userId) {
    if (userId == null) {
      return null;
    }
    String normalized = userId.trim().toLowerCase(Locale.ROOT);
    return normalized.isBlank() ? null : normalized;
  }

  private String sanitizeUserName(String userName) {
    String safe = sanitizeText(userName, 80);
    return safe == null ? "community member" : safe;
  }

  private String sanitizeText(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    String sanitized = CONTROL.matcher(value).replaceAll("").trim();
    if (sanitized.isBlank()) {
      return null;
    }
    if (maxLength > 0 && sanitized.length() > maxLength) {
      sanitized = sanitized.substring(0, maxLength).trim();
    }
    return sanitized.isBlank() ? null : sanitized;
  }

  private static String safe(String value) {
    if (value == null || value.isBlank()) {
      return "n/a";
    }
    return value.trim();
  }

  private record QueuedThread(String threadId, Instant queuedAt) {}

  private record AttemptEntry(String userId, Instant at) {}

  public record CreateThreadRequest(String mode, String title, String body) {}

  public record FeedPage(
      List<CommunityLightningThread> items,
      int total,
      int limit,
      int offset,
      int queueDepth,
      Instant nextPublishAt) {}

  public record CreateThreadResult(
      CommunityLightningThread item, boolean queued, int queuePosition, Instant nextPublishAt) {}

  public record CommentResult(CommunityLightningThread thread, CommunityLightningComment comment) {}

  public record LikeResult(CommunityLightningThread thread, CommunityLightningComment comment, boolean liked) {}

  public record ReportResult(
      String reportId, boolean duplicate, CommunityLightningReport report, int totalReports) {}

  public record Stats(
      int threads,
      long publishedThreads,
      int comments,
      int reports,
      int queueDepth,
      Instant nextPublishAt,
      Instant raidCooldownUntil) {}

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public static class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
      super(message);
    }
  }

  public static class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
      super(message);
    }
  }

  public static class RateLimitExceededException extends RuntimeException {
    private final String userMessage;

    public RateLimitExceededException(String code, String userMessage) {
      super(code);
      this.userMessage = userMessage;
    }

    public String userMessage() {
      return userMessage;
    }
  }
}
