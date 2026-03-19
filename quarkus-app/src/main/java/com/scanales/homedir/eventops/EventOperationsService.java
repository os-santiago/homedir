package com.scanales.homedir.eventops;

import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.PersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@ApplicationScoped
public class EventOperationsService {
  private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
  private static final int MAX_CAPACITY = 100_000;

  @Inject PersistenceService persistenceService;
  @Inject EventService eventService;

  private final ConcurrentHashMap<String, EventStaffAssignment> staffAssignments =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, EventSpace> spaces = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, EventSpaceResponsibleShift> spaceShifts =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, EventSpaceActivity> activities =
      new ConcurrentHashMap<>();

  private final Object lock = new Object();
  private volatile long lastKnownMtime = Long.MIN_VALUE;

  @PostConstruct
  void init() {
    synchronized (lock) {
      refreshFromDisk(true);
    }
  }

  public List<EventStaffAssignment> listStaff(String eventId, boolean includeInactive) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      if (normalizedEventId == null) {
        return List.of();
      }
      return staffAssignments.values().stream()
          .filter(item -> normalizedEventId.equals(item.eventId()))
          .filter(item -> includeInactive || item.active())
          .sorted(
              Comparator.comparing(
                      EventStaffAssignment::updatedAt,
                      Comparator.nullsLast(Comparator.reverseOrder()))
                  .thenComparing(EventStaffAssignment::id))
          .toList();
    }
  }

  public Optional<EventStaffAssignment> findStaffByEventAndUser(
      String eventId, String userId, EventStaffRole role) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedEventId == null || normalizedUserId == null || role == null) {
        return Optional.empty();
      }
      return Optional.ofNullable(staffAssignments.get(staffAssignmentId(normalizedEventId, normalizedUserId, role)));
    }
  }

  public EventStaffAssignment upsertStaff(
      String eventId,
      String userId,
      String userName,
      EventStaffRole role,
      String source,
      boolean active) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedEventId == null) {
        throw new ValidationException("event_id_required");
      }
      if (eventService.getEvent(normalizedEventId) == null) {
        throw new NotFoundException("event_not_found");
      }
      if (normalizedUserId == null) {
        throw new ValidationException("user_id_required");
      }
      if (role == null) {
        throw new ValidationException("role_required");
      }
      Instant now = Instant.now();
      String id = staffAssignmentId(normalizedEventId, normalizedUserId, role);
      EventStaffAssignment current = staffAssignments.get(id);
      EventStaffAssignment updated =
          new EventStaffAssignment(
              id,
              normalizedEventId,
              normalizedUserId,
              sanitizeName(userName),
              role.apiValue(),
              sanitizeSource(source),
              active,
              current != null ? current.createdAt() : now,
              now);
      staffAssignments.put(id, updated);
      persistSync();
      return updated;
    }
  }

  public EventStaffAssignment upsertVolunteerSelection(
      String eventId, String userId, String userName, boolean selected) {
    return upsertStaff(
        eventId,
        userId,
        userName,
        EventStaffRole.VOLUNTEER,
        "volunteer_selection",
        selected);
  }

  public boolean hasStaffRole(
      String eventId, Set<String> userIds, Set<EventStaffRole> allowedRoles, boolean requireActive) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      if (normalizedEventId == null || userIds == null || userIds.isEmpty()) {
        return false;
      }
      Set<String> normalizedUsers = new LinkedHashSet<>();
      for (String raw : userIds) {
        String normalized = sanitizeUserId(raw);
        if (normalized != null) {
          normalizedUsers.add(normalized);
        }
      }
      if (normalizedUsers.isEmpty()) {
        return false;
      }
      for (EventStaffAssignment item : staffAssignments.values()) {
        if (!normalizedEventId.equals(item.eventId())) {
          continue;
        }
        if (requireActive && !item.active()) {
          continue;
        }
        if (!normalizedUsers.contains(sanitizeUserId(item.userId()))) {
          continue;
        }
        EventStaffRole role = EventStaffRole.fromApi(item.role()).orElse(null);
        if (role == null) {
          continue;
        }
        if (allowedRoles == null || allowedRoles.isEmpty() || allowedRoles.contains(role)) {
          return true;
        }
      }
      return false;
    }
  }

  public List<EventSpace> listSpaces(String eventId, boolean includeInactive) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      if (normalizedEventId == null) {
        return List.of();
      }
      return spaces.values().stream()
          .filter(item -> normalizedEventId.equals(item.eventId()))
          .filter(item -> includeInactive || item.active())
          .sorted(
              Comparator.comparing(
                      EventSpace::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                  .thenComparing(EventSpace::id))
          .toList();
    }
  }

  public EventSpace upsertSpace(
      String eventId,
      String spaceId,
      String name,
      EventSpaceType type,
      Integer capacity,
      boolean active) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      if (normalizedEventId == null) {
        throw new ValidationException("event_id_required");
      }
      if (eventService.getEvent(normalizedEventId) == null) {
        throw new NotFoundException("event_not_found");
      }
      String normalizedName = sanitizeText(name, 120);
      if (normalizedName == null) {
        throw new ValidationException("space_name_required");
      }
      EventSpaceType normalizedType = type == null ? EventSpaceType.OTHER : type;
      Integer normalizedCapacity = sanitizeCapacity(capacity);
      String normalizedId = sanitizeScopedId(normalizedEventId, spaceId, normalizedName);
      Instant now = Instant.now();
      EventSpace current = spaces.get(normalizedId);
      EventSpace updated =
          new EventSpace(
              normalizedId,
              normalizedEventId,
              normalizedName,
              normalizedType.apiValue(),
              normalizedCapacity,
              active,
              current != null ? current.createdAt() : now,
              now);
      spaces.put(normalizedId, updated);
      persistSync();
      return updated;
    }
  }

  public List<EventSpaceResponsibleShift> listSpaceShifts(String eventId, String spaceId) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      String normalizedSpaceId = sanitizeId(spaceId);
      if (normalizedEventId == null) {
        return List.of();
      }
      return spaceShifts.values().stream()
          .filter(item -> normalizedEventId.equals(item.eventId()))
          .filter(item -> normalizedSpaceId == null || normalizedSpaceId.equals(item.spaceId()))
          .sorted(
              Comparator.comparing(
                      EventSpaceResponsibleShift::startAt,
                      Comparator.nullsLast(Comparator.naturalOrder()))
                  .thenComparing(EventSpaceResponsibleShift::id))
          .toList();
    }
  }

  public EventSpaceResponsibleShift createSpaceShift(
      String eventId, String spaceId, String userId, Instant startAt, Instant endAt) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      String normalizedSpaceId = sanitizeId(spaceId);
      String normalizedUserId = sanitizeUserId(userId);
      if (normalizedEventId == null || normalizedSpaceId == null || normalizedUserId == null) {
        throw new ValidationException("invalid_shift");
      }
      EventSpace space = spaces.get(normalizedSpaceId);
      if (space == null || !normalizedEventId.equals(space.eventId())) {
        throw new NotFoundException("space_not_found");
      }
      if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
        throw new ValidationException("invalid_shift_window");
      }
      EventStaffAssignment assignment =
          staffAssignments.values().stream()
              .filter(item -> normalizedEventId.equals(item.eventId()))
              .filter(item -> normalizedUserId.equals(item.userId()))
              .filter(EventStaffAssignment::active)
              .findFirst()
              .orElseThrow(() -> new ValidationException("staff_member_required"));
      Instant now = Instant.now();
      EventSpaceResponsibleShift created =
          new EventSpaceResponsibleShift(
              UUID.randomUUID().toString(),
              normalizedEventId,
              normalizedSpaceId,
              normalizedUserId,
              sanitizeName(assignment.userName()),
              startAt,
              endAt);
      spaceShifts.put(created.id(), created);
      persistSync();
      return created;
    }
  }

  public List<EventSpaceActivity> listActivities(
      String eventId, Optional<EventActivityVisibility> visibilityFilter) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      if (normalizedEventId == null) {
        return List.of();
      }
      String visibility = visibilityFilter.map(EventActivityVisibility::apiValue).orElse(null);
      return activities.values().stream()
          .filter(item -> normalizedEventId.equals(item.eventId()))
          .filter(item -> visibility == null || visibility.equals(sanitizeVisibility(item.visibility())))
          .sorted(
              Comparator.comparing(
                      EventSpaceActivity::startAt, Comparator.nullsLast(Comparator.naturalOrder()))
                  .thenComparing(EventSpaceActivity::id))
          .toList();
    }
  }

  public EventSpaceActivity upsertActivity(
      String eventId,
      String activityId,
      String spaceId,
      String title,
      String details,
      EventActivityVisibility visibility,
      Instant startAt,
      Instant endAt) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      String normalizedSpaceId = sanitizeId(spaceId);
      if (normalizedEventId == null || normalizedSpaceId == null) {
        throw new ValidationException("invalid_activity");
      }
      if (eventService.getEvent(normalizedEventId) == null) {
        throw new NotFoundException("event_not_found");
      }
      EventSpace space = spaces.get(normalizedSpaceId);
      if (space == null || !normalizedEventId.equals(space.eventId())) {
        throw new NotFoundException("space_not_found");
      }
      String normalizedTitle = sanitizeText(title, 160);
      if (normalizedTitle == null) {
        throw new ValidationException("activity_title_required");
      }
      if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
        throw new ValidationException("invalid_activity_window");
      }
      EventActivityVisibility normalizedVisibility =
          visibility == null ? EventActivityVisibility.STAFF : visibility;
      String normalizedId =
          normalizeScopedResourceId(normalizedEventId, "activity", sanitizeId(activityId));
      if (normalizedId == null) {
        throw new ValidationException("invalid_activity_id");
      }
      Instant now = Instant.now();
      EventSpaceActivity current = activities.get(normalizedId);
      EventSpaceActivity updated =
          new EventSpaceActivity(
              normalizedId,
              normalizedEventId,
              normalizedSpaceId,
              normalizedTitle,
              sanitizeText(details, 1200),
              normalizedVisibility.apiValue(),
              startAt,
              endAt,
              current != null ? current.createdAt() : now,
              now);
      activities.put(normalizedId, updated);
      persistSync();
      return updated;
    }
  }

  public EventRunSheetView buildRunSheet(
      String eventId, Optional<EventActivityVisibility> visibilityFilter, boolean includeInactiveStaff) {
    synchronized (lock) {
      refreshFromDisk(false);
      String normalizedEventId = sanitizeEventId(eventId);
      if (normalizedEventId == null) {
        throw new ValidationException("event_id_required");
      }
      if (eventService.getEvent(normalizedEventId) == null) {
        throw new NotFoundException("event_not_found");
      }
      List<EventStaffAssignment> staff = listStaff(normalizedEventId, includeInactiveStaff);
      List<EventSpace> eventSpaces = listSpaces(normalizedEventId, true);
      List<EventSpaceResponsibleShift> shifts = listSpaceShifts(normalizedEventId, null);
      List<EventSpaceActivity> scheduled = listActivities(normalizedEventId, visibilityFilter);
      return new EventRunSheetView(staff, eventSpaces, shifts, scheduled);
    }
  }

  public void reloadFromDisk() {
    synchronized (lock) {
      refreshFromDisk(true);
    }
  }

  public void clearAllForTests() {
    synchronized (lock) {
      staffAssignments.clear();
      spaces.clear();
      spaceShifts.clear();
      activities.clear();
      persistSync();
    }
  }

  private void persistSync() {
    EventOperationsStateSnapshot snapshot =
        new EventOperationsStateSnapshot(
            EventOperationsStateSnapshot.SCHEMA_VERSION,
            Instant.now(),
            new LinkedHashMap<>(staffAssignments),
            new LinkedHashMap<>(spaces),
            new LinkedHashMap<>(spaceShifts),
            new LinkedHashMap<>(activities));
    persistenceService.saveEventOperationsStateSync(snapshot);
    lastKnownMtime = persistenceService.eventOperationsStateLastModifiedMillis();
  }

  private void refreshFromDisk(boolean force) {
    long mtime = persistenceService.eventOperationsStateLastModifiedMillis();
    if (!force && mtime == lastKnownMtime) {
      return;
    }
    EventOperationsStateSnapshot snapshot =
        persistenceService.loadEventOperationsState().orElse(EventOperationsStateSnapshot.empty());
    staffAssignments.clear();
    spaces.clear();
    spaceShifts.clear();
    activities.clear();
    if (snapshot.staffAssignments() != null) {
      snapshot.staffAssignments().forEach(
          (id, item) -> {
            if (id == null || id.isBlank() || item == null) {
              return;
            }
            staffAssignments.put(id, item);
          });
    }
    if (snapshot.spaces() != null) {
      snapshot.spaces().forEach(
          (id, item) -> {
            if (id == null || id.isBlank() || item == null) {
              return;
            }
            spaces.put(id, item);
          });
    }
    if (snapshot.spaceShifts() != null) {
      snapshot.spaceShifts().forEach(
          (id, item) -> {
            if (id == null || id.isBlank() || item == null) {
              return;
            }
            spaceShifts.put(id, item);
          });
    }
    if (snapshot.activities() != null) {
      snapshot.activities().forEach(
          (id, item) -> {
            if (id == null || id.isBlank() || item == null) {
              return;
            }
            activities.put(id, item);
          });
    }
    lastKnownMtime = mtime;
  }

  private static String staffAssignmentId(String eventId, String userId, EventStaffRole role) {
    return eventId + ":staff:" + userId + ":" + role.apiValue();
  }

  private static String sanitizeEventId(String raw) {
    String id = sanitizeId(raw);
    return id == null ? null : id.toLowerCase(Locale.ROOT);
  }

  private static String sanitizeId(String raw) {
    if (raw == null) {
      return null;
    }
    String cleaned = raw.trim();
    return cleaned.isBlank() ? null : cleaned;
  }

  private static String sanitizeUserId(String raw) {
    String value = sanitizeId(raw);
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }

  private static String sanitizeText(String raw, int maxLength) {
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

  private static String sanitizeName(String raw) {
    String name = sanitizeText(raw, 120);
    return name == null ? "Staff member" : name;
  }

  private static String sanitizeSource(String raw) {
    String source = sanitizeText(raw, 80);
    return source == null ? "manual" : source.toLowerCase(Locale.ROOT);
  }

  private static String sanitizeScopedId(String eventId, String explicitId, String fallbackName) {
    String cleanedExplicit = sanitizeId(explicitId);
    if (cleanedExplicit != null) {
      return normalizeScopedResourceId(eventId, "space", cleanedExplicit);
    }
    String slug = fallbackName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    slug = slug.replaceAll("^-+", "").replaceAll("-+$", "");
    if (slug.isBlank()) {
      slug = UUID.randomUUID().toString();
    }
    return eventId + ":space:" + slug;
  }

  private static String normalizeScopedResourceId(String eventId, String resource, String rawId) {
    String cleaned = sanitizeId(rawId);
    if (cleaned == null) {
      return null;
    }
    if (cleaned.contains(":")) {
      return cleaned;
    }
    return eventId + ":" + resource + ":" + cleaned.toLowerCase(Locale.ROOT);
  }

  private static Integer sanitizeCapacity(Integer raw) {
    if (raw == null) {
      return null;
    }
    if (raw < 0 || raw > MAX_CAPACITY) {
      throw new ValidationException("invalid_space_capacity");
    }
    return raw;
  }

  private static String sanitizeVisibility(String raw) {
    if (raw == null || raw.isBlank()) {
      return EventActivityVisibility.STAFF.apiValue();
    }
    return EventActivityVisibility.fromApi(raw)
        .orElse(EventActivityVisibility.STAFF)
        .apiValue();
  }

  public record EventRunSheetView(
      List<EventStaffAssignment> staff,
      List<EventSpace> spaces,
      List<EventSpaceResponsibleShift> shifts,
      List<EventSpaceActivity> activities) {}

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
