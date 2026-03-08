package com.scanales.eventflow.volunteers;

import com.scanales.eventflow.service.EventService;
import com.scanales.eventflow.service.PersistenceService;
import com.scanales.eventflow.util.PaginationGuardrails;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@ApplicationScoped
public class VolunteerLoungeService {
  private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
  private static final int MAX_POST_BODY_LENGTH = 200;
  private static final int MAX_ANNOUNCEMENT_BODY_LENGTH = 500;
  private static final int MAX_MESSAGES_PER_EVENT = 500;
  private static final Duration POST_RATE_LIMIT = Duration.ofMinutes(1);

  @Inject PersistenceService persistenceService;
  @Inject EventService eventService;

  private final ConcurrentHashMap<String, VolunteerLoungeMessage> messages = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Instant> lastPostByUserEvent = new ConcurrentHashMap<>();
  private final Object lock = new Object();
  private volatile long lastKnownMtime = Long.MIN_VALUE;

  @PostConstruct
  void init() {
    synchronized (lock) {
      refreshFromDisk(true);
    }
  }

  public List<VolunteerLoungeMessage> listByEvent(String eventId, int requestedLimit, int requestedOffset) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        return List.of();
      }
      List<VolunteerLoungeMessage> filtered =
          messages.values().stream()
              .filter(item -> normalizedEventId.equals(item.eventId()))
              .sorted(
                  Comparator.comparing(
                          VolunteerLoungeMessage::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                      .thenComparing(VolunteerLoungeMessage::id))
              .toList();
      return paginate(filtered, requestedLimit, requestedOffset);
    }
  }

  public int countByEvent(String eventId) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        return 0;
      }
      return countByEventInternal(normalizedEventId, null);
    }
  }

  public List<VolunteerLoungeMessage> listByEventAndType(
      String eventId, VolunteerLoungeMessageType messageType, int requestedLimit, int requestedOffset) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null || messageType == null) {
        return List.of();
      }
      String typeFilter = messageType.apiValue();
      List<VolunteerLoungeMessage> filtered =
          messages.values().stream()
              .filter(item -> normalizedEventId.equals(item.eventId()))
              .filter(item -> typeFilter.equals(normalizedType(item)))
              .sorted(
                  Comparator.comparing(
                          VolunteerLoungeMessage::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                      .thenComparing(VolunteerLoungeMessage::id))
              .toList();
      return paginate(filtered, requestedLimit, requestedOffset);
    }
  }

  public int countByEventAndType(String eventId, VolunteerLoungeMessageType messageType) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null || messageType == null) {
        return 0;
      }
      return countByEventInternal(normalizedEventId, messageType.apiValue());
    }
  }

  public VolunteerLoungeMessage create(
      String eventId,
      String userId,
      String userName,
      String body,
      String parentId,
      VolunteerLoungeMessageType messageType) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      if (normalizedEventId == null) {
        throw new ValidationException("event_id_required");
      }
      if (eventService.getEvent(normalizedEventId) == null) {
        throw new NotFoundException("event_not_found");
      }
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedUserId == null) {
        throw new ValidationException("user_id_required");
      }
      VolunteerLoungeMessageType normalizedType =
          messageType == null ? VolunteerLoungeMessageType.POST : messageType;
      int maxBodyLength =
          normalizedType == VolunteerLoungeMessageType.ANNOUNCEMENT
              ? MAX_ANNOUNCEMENT_BODY_LENGTH
              : MAX_POST_BODY_LENGTH;
      String normalizedBody = sanitizeBody(body, maxBodyLength);
      if (normalizedBody == null) {
        throw new ValidationException("invalid_body");
      }
      String normalizedParentId = sanitizeId(parentId);
      if (normalizedType == VolunteerLoungeMessageType.ANNOUNCEMENT && normalizedParentId != null) {
        throw new ValidationException("invalid_parent");
      }
      if (normalizedType == VolunteerLoungeMessageType.POST && normalizedParentId != null) {
        VolunteerLoungeMessage parent = messages.get(normalizedParentId);
        if (parent == null
            || !normalizedEventId.equals(parent.eventId())
            || VolunteerLoungeMessageType.ANNOUNCEMENT.apiValue().equals(normalizedType(parent))) {
          throw new ValidationException("invalid_parent");
        }
      }

      int eventCount = countByEventInternal(normalizedEventId, null);
      if (eventCount >= MAX_MESSAGES_PER_EVENT) {
        throw new ValidationException("event_capacity_reached");
      }

      String rateLimitKey = normalizedEventId + ":" + normalizedUserId;
      Instant now = Instant.now();
      if (normalizedType == VolunteerLoungeMessageType.POST) {
        Instant previous = lastPostByUserEvent.get(rateLimitKey);
        if (previous != null && previous.plus(POST_RATE_LIMIT).isAfter(now)) {
          throw new ValidationException("rate_limit");
        }
      }

      VolunteerLoungeMessage created =
          new VolunteerLoungeMessage(
              UUID.randomUUID().toString(),
              normalizedEventId,
              normalizedType.apiValue(),
              normalizedParentId,
              normalizedUserId,
              sanitizeUserName(userName),
              normalizedBody,
              now,
              now);
      messages.put(created.id(), created);
      if (normalizedType == VolunteerLoungeMessageType.POST) {
        lastPostByUserEvent.put(rateLimitKey, now);
      }
      persistSync();
      return created;
    }
  }

  public Optional<VolunteerLoungeMessage> findById(String id) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedId = sanitizeId(id);
      if (normalizedId == null) {
        return Optional.empty();
      }
      return Optional.ofNullable(messages.get(normalizedId));
    }
  }

  public void reloadFromDisk() {
    synchronized (lock) {
      refreshFromDisk(true);
    }
  }

  public boolean delete(String eventId, String id) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeId(eventId);
      String normalizedId = sanitizeId(id);
      if (normalizedEventId == null || normalizedId == null) {
        return false;
      }
      VolunteerLoungeMessage current = messages.get(normalizedId);
      if (current == null || !normalizedEventId.equals(current.eventId())) {
        return false;
      }
      messages.remove(normalizedId);
      persistSync();
      return true;
    }
  }

  public void clearAllForTests() {
    synchronized (lock) {
      messages.clear();
      lastPostByUserEvent.clear();
      persistSync();
    }
  }

  private void persistSync() {
    try {
      persistenceService.saveVolunteerLoungeMessagesSync(new LinkedHashMap<>(messages));
      lastKnownMtime = persistenceService.volunteerLoungeMessagesLastModifiedMillis();
    } catch (IllegalStateException e) {
      throw new IllegalStateException("failed_to_persist_volunteer_lounge_state", e);
    }
  }

  private void refreshFromDisk(boolean force) {
    long mtime = persistenceService.volunteerLoungeMessagesLastModifiedMillis();
    if (!force && mtime == lastKnownMtime) {
      return;
    }
    messages.clear();
    messages.putAll(persistenceService.loadVolunteerLoungeMessages());
    lastKnownMtime = mtime;
  }

  private static String sanitizeId(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    return value.isBlank() ? null : value;
  }

  private static String sanitizeUserId(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim().toLowerCase(Locale.ROOT);
    return value.isBlank() ? null : value;
  }

  private static String sanitizeUserName(String raw) {
    if (raw == null || raw.isBlank()) {
      return "Volunteer";
    }
    String cleaned = CONTROL.matcher(raw).replaceAll("").trim();
    if (cleaned.isBlank()) {
      return "Volunteer";
    }
    return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
  }

  private int countByEventInternal(String eventId, String typeFilter) {
    int count = 0;
    for (VolunteerLoungeMessage item : messages.values()) {
      if (!eventId.equals(item.eventId())) {
        continue;
      }
      if (typeFilter != null && !typeFilter.equals(normalizedType(item))) {
        continue;
      }
      count++;
    }
    return count;
  }

  private static String sanitizeBody(String raw, int maxLength) {
    if (raw == null) {
      return null;
    }
    String cleaned = CONTROL.matcher(raw).replaceAll("").trim();
    if (cleaned.isBlank()) {
      return null;
    }
    if (cleaned.length() > maxLength) {
      cleaned = cleaned.substring(0, maxLength).trim();
    }
    return cleaned.isBlank() ? null : cleaned;
  }

  private static String normalizedType(VolunteerLoungeMessage item) {
    if (item == null) {
      return VolunteerLoungeMessageType.POST.apiValue();
    }
    return item.normalizedMessageType();
  }

  private static List<VolunteerLoungeMessage> paginate(
      List<VolunteerLoungeMessage> source, int requestedLimit, int requestedOffset) {
    int limit =
        PaginationGuardrails.clampLimit(
            requestedLimit,
            PaginationGuardrails.DEFAULT_PAGE_LIMIT,
            PaginationGuardrails.MAX_PAGE_LIMIT);
    int offset = PaginationGuardrails.clampOffset(requestedOffset, PaginationGuardrails.MAX_OFFSET);
    if (offset >= source.size()) {
      return List.of();
    }
    int end = Math.min(source.size(), offset + limit);
    return source.subList(offset, end);
  }

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
}
