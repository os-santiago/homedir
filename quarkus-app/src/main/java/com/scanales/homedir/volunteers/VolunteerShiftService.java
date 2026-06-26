package com.scanales.homedir.volunteers;

import com.scanales.homedir.model.Event;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.PersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages volunteer shifts and availability for multi-day events. Enforces business rules: 2-hour
 * segments, min 2/max 4 segments per day.
 */
@ApplicationScoped
public class VolunteerShiftService {

  @Inject PersistenceService persistenceService;
  @Inject EventService eventService;

  private final ConcurrentHashMap<String, VolunteerShift> shifts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, VolunteerAvailability> availabilities =
      new ConcurrentHashMap<>();
  private final Object lock = new Object();
  private volatile long lastKnownShiftsMtime = Long.MIN_VALUE;
  private volatile long lastKnownAvailabilitiesMtime = Long.MIN_VALUE;

  @PostConstruct
  void init() {
    synchronized (lock) {
      refreshShiftsFromDisk(true);
      refreshAvailabilitiesFromDisk(true);
    }
  }

  /**
   * Generates default shifts for an event based on its duration and date. Creates 2-hour segments
   * from 9 AM to 7 PM for each day.
   */
  public List<VolunteerShift> generateShiftsForEvent(String eventId) {
    synchronized (lock) {
      Event event = eventService.getEvent(eventId);
      if (event == null) {
        throw new ValidationException("event_not_found");
      }

      List<VolunteerShift> generated = new ArrayList<>();
      LocalDate startDate = event.getDate() != null ? event.getDate() : LocalDate.now();
      int days = Math.max(1, event.getDays());

      for (int dayIndex = 0; dayIndex < days; dayIndex++) {
        LocalDate currentDay = startDate.plusDays(dayIndex);

        // Generate shifts from 9 AM to 7 PM (5 shifts of 2 hours each)
        LocalTime[] startTimes = {
          LocalTime.of(9, 0),
          LocalTime.of(11, 0),
          LocalTime.of(13, 0),
          LocalTime.of(15, 0),
          LocalTime.of(17, 0)
        };

        for (int shiftIndex = 0; shiftIndex < startTimes.length; shiftIndex++) {
          LocalTime startTime = startTimes[shiftIndex];
          LocalTime endTime = startTime.plusHours(VolunteerShift.SHIFT_DURATION_HOURS);

          String shiftId = UUID.randomUUID().toString();
          String label =
              String.format(
                  Locale.ROOT,
                  "Day %d - %s to %s",
                  dayIndex + 1,
                  startTime.toString(),
                  endTime.toString());

          VolunteerShift shift =
              new VolunteerShift(
                  shiftId,
                  eventId,
                  dayIndex,
                  LocalDateTime.of(currentDay, startTime),
                  LocalDateTime.of(currentDay, endTime),
                  10, // default max volunteers per shift
                  label);

          generated.add(shift);
          shifts.put(shiftId, shift);
        }
      }

      persistShiftsSync();
      return generated;
    }
  }

  /** Lists all shifts for a given event. */
  public List<VolunteerShift> listShiftsByEvent(String eventId) {
    synchronized (lock) {
      refreshShiftsFromDisk(false);
      String normalized = sanitizeId(eventId);
      if (normalized == null) {
        return List.of();
      }

      return shifts.values().stream()
          .filter(shift -> normalized.equals(shift.eventId()))
          .sorted(
              (a, b) -> {
                int dayCompare = Integer.compare(a.dayIndex(), b.dayIndex());
                if (dayCompare != 0) return dayCompare;
                return a.startTime().compareTo(b.startTime());
              })
          .toList();
    }
  }

  /** Sets availability for a volunteer. Validates min/max segments per day. */
  public VolunteerAvailability setAvailability(
      String eventId, String userId, String userName, List<String> shiftIds) {
    synchronized (lock) {
      refreshShiftsFromDisk(false);
      refreshAvailabilitiesFromDisk(false);

      String normalizedEventId = sanitizeId(eventId);
      String normalizedUserId = sanitizeUserId(userId);

      if (normalizedEventId == null) {
        throw new ValidationException("event_id_required");
      }
      if (normalizedUserId == null) {
        throw new ValidationException("user_id_required");
      }

      Event event = eventService.getEvent(normalizedEventId);
      if (event == null) {
        throw new ValidationException("event_not_found");
      }

      List<String> normalizedShiftIds =
          shiftIds != null
              ? shiftIds.stream().filter(Objects::nonNull).distinct().toList()
              : List.of();

      // Validate that all shift IDs exist and belong to this event
      Map<Integer, List<VolunteerShift>> shiftsByDay = new LinkedHashMap<>();
      for (String shiftId : normalizedShiftIds) {
        VolunteerShift shift = shifts.get(shiftId);
        if (shift == null || !normalizedEventId.equals(shift.eventId())) {
          throw new ValidationException("invalid_shift_id");
        }
        shiftsByDay.computeIfAbsent(shift.dayIndex(), k -> new ArrayList<>()).add(shift);
      }

      // Validate min/max segments per day
      for (Map.Entry<Integer, List<VolunteerShift>> entry : shiftsByDay.entrySet()) {
        int dayIndex = entry.getKey();
        int segmentCount = entry.getValue().size();

        if (segmentCount < VolunteerAvailability.MIN_SEGMENTS_PER_DAY) {
          throw new ValidationException(
              String.format(
                  Locale.ROOT,
                  "day_%d_min_segments: minimum %d segments required, got %d",
                  dayIndex,
                  VolunteerAvailability.MIN_SEGMENTS_PER_DAY,
                  segmentCount));
        }

        if (segmentCount > VolunteerAvailability.MAX_SEGMENTS_PER_DAY) {
          throw new ValidationException(
              String.format(
                  Locale.ROOT,
                  "day_%d_max_segments: maximum %d segments allowed, got %d",
                  dayIndex,
                  VolunteerAvailability.MAX_SEGMENTS_PER_DAY,
                  segmentCount));
        }
      }

      // Find or create availability record
      Optional<VolunteerAvailability> existing =
          findAvailabilityByEventAndUser(normalizedEventId, normalizedUserId);

      Instant now = Instant.now();
      VolunteerAvailability availability;

      if (existing.isPresent()) {
        VolunteerAvailability current = existing.get();
        availability =
            new VolunteerAvailability(
                current.id(),
                normalizedEventId,
                normalizedUserId,
                sanitizeText(userName, 120),
                normalizedShiftIds,
                current.createdAt(),
                now);
      } else {
        availability =
            new VolunteerAvailability(
                UUID.randomUUID().toString(),
                normalizedEventId,
                normalizedUserId,
                sanitizeText(userName, 120),
                normalizedShiftIds,
                now,
                now);
      }

      availabilities.put(availability.id(), availability);
      persistAvailabilitiesSync();
      return availability;
    }
  }

  /** Gets availability for a specific volunteer on an event. */
  public Optional<VolunteerAvailability> getAvailability(String eventId, String userId) {
    synchronized (lock) {
      refreshAvailabilitiesFromDisk(false);
      return findAvailabilityByEventAndUser(eventId, userId);
    }
  }

  /** Lists all availability records for an event. */
  public List<VolunteerAvailability> listAvailabilitiesByEvent(String eventId) {
    synchronized (lock) {
      refreshAvailabilitiesFromDisk(false);
      String normalized = sanitizeId(eventId);
      if (normalized == null) {
        return List.of();
      }

      return availabilities.values().stream()
          .filter(av -> normalized.equals(av.eventId()))
          .sorted(
              (a, b) -> {
                Instant aTime = a.createdAt() != null ? a.createdAt() : Instant.MIN;
                Instant bTime = b.createdAt() != null ? b.createdAt() : Instant.MIN;
                return bTime.compareTo(aTime);
              })
          .toList();
    }
  }

  /** Gets shift coverage statistics for an event. */
  public ShiftCoverageStats getCoverageStats(String eventId) {
    synchronized (lock) {
      refreshShiftsFromDisk(false);
      refreshAvailabilitiesFromDisk(false);

      String normalized = sanitizeId(eventId);
      if (normalized == null) {
        return new ShiftCoverageStats(Map.of(), 0, 0);
      }

      List<VolunteerShift> eventShifts = listShiftsByEvent(normalized);
      List<VolunteerAvailability> eventAvailabilities = listAvailabilitiesByEvent(normalized);

      Map<String, Integer> coverageByShift = new LinkedHashMap<>();
      for (VolunteerShift shift : eventShifts) {
        int count =
            (int)
                eventAvailabilities.stream()
                    .filter(av -> av.selectedShiftIds().contains(shift.id()))
                    .count();
        coverageByShift.put(shift.id(), count);
      }

      int totalShifts = eventShifts.size();
      long uncoveredShifts = coverageByShift.values().stream().filter(count -> count == 0).count();

      return new ShiftCoverageStats(coverageByShift, totalShifts, (int) uncoveredShifts);
    }
  }

  public void clearAllForTests() {
    synchronized (lock) {
      shifts.clear();
      availabilities.clear();
      persistShiftsSync();
      persistAvailabilitiesSync();
    }
  }

  private Optional<VolunteerAvailability> findAvailabilityByEventAndUser(
      String eventId, String userId) {
    String normalizedEventId = sanitizeId(eventId);
    String normalizedUserId = sanitizeUserId(userId);
    if (normalizedEventId == null || normalizedUserId == null) {
      return Optional.empty();
    }

    return availabilities.values().stream()
        .filter(av -> normalizedEventId.equals(av.eventId()))
        .filter(av -> normalizedUserId.equals(av.volunteerUserId()))
        .findFirst();
  }

  private void persistShiftsSync() {
    try {
      persistenceService.saveVolunteerShiftsSync(new LinkedHashMap<>(shifts));
      lastKnownShiftsMtime = persistenceService.volunteerShiftsLastModifiedMillis();
    } catch (IllegalStateException e) {
      throw new IllegalStateException("failed_to_persist_volunteer_shifts", e);
    }
  }

  private void persistAvailabilitiesSync() {
    try {
      persistenceService.saveVolunteerAvailabilitiesSync(new LinkedHashMap<>(availabilities));
      lastKnownAvailabilitiesMtime = persistenceService.volunteerAvailabilitiesLastModifiedMillis();
    } catch (IllegalStateException e) {
      throw new IllegalStateException("failed_to_persist_volunteer_availabilities", e);
    }
  }

  private void refreshShiftsFromDisk(boolean force) {
    long mtime = persistenceService.volunteerShiftsLastModifiedMillis();
    if (!force && mtime == lastKnownShiftsMtime) {
      return;
    }
    shifts.clear();
    shifts.putAll(persistenceService.loadVolunteerShifts());
    lastKnownShiftsMtime = mtime;
  }

  private void refreshAvailabilitiesFromDisk(boolean force) {
    long mtime = persistenceService.volunteerAvailabilitiesLastModifiedMillis();
    if (!force && mtime == lastKnownAvailabilitiesMtime) {
      return;
    }
    availabilities.clear();
    availabilities.putAll(persistenceService.loadVolunteerAvailabilities());
    lastKnownAvailabilitiesMtime = mtime;
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

  private static String sanitizeText(String raw, int maxLength) {
    if (raw == null) {
      return null;
    }
    String cleaned = raw.trim();
    if (cleaned.isEmpty()) {
      return null;
    }
    if (cleaned.length() > maxLength) {
      cleaned = cleaned.substring(0, maxLength).trim();
    }
    return cleaned.isEmpty() ? null : cleaned;
  }

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public record ShiftCoverageStats(
      Map<String, Integer> coverageByShift, int totalShifts, int uncoveredShifts) {}
}
