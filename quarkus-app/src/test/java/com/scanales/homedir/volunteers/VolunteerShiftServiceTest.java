package com.scanales.homedir.volunteers;

import static org.junit.jupiter.api.Assertions.*;

import com.scanales.homedir.model.Event;
import com.scanales.homedir.service.EventService;
import com.scanales.homedir.service.PersistenceService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class VolunteerShiftServiceTest {

  @Inject VolunteerShiftService service;
  @Inject EventService eventService;
  @Inject PersistenceService persistenceService;

  @BeforeEach
  void setup() {
    service.clearAllForTests();
  }

  @Test
  void testGenerateShiftsForSingleDayEvent() {
    // Create a 1-day event
    Event event = new Event("test-event-1", "Test Event", "Test", 1);
    event.setDate(LocalDate.of(2026, 7, 15));
    eventService.saveEvent(event);

    // Generate shifts
    List<VolunteerShift> shifts = service.generateShiftsForEvent("test-event-1");

    assertNotNull(shifts);
    assertEquals(5, shifts.size(), "Should create 5 two-hour shifts for one day (9 AM - 7 PM)");

    // Verify first shift
    VolunteerShift firstShift = shifts.get(0);
    assertEquals("test-event-1", firstShift.eventId());
    assertEquals(0, firstShift.dayIndex());
    assertEquals(9, firstShift.startTime().getHour());
    assertEquals(11, firstShift.endTime().getHour());
  }

  @Test
  void testGenerateShiftsForMultiDayEvent() {
    // Create a 2-day event
    Event event = new Event("devopsdays-santiago-2026", "DevOpsDays Santiago", "Test", 2);
    event.setDate(LocalDate.of(2026, 10, 22));
    eventService.saveEvent(event);

    // Generate shifts
    List<VolunteerShift> shifts = service.generateShiftsForEvent("devopsdays-santiago-2026");

    assertNotNull(shifts);
    assertEquals(10, shifts.size(), "Should create 10 shifts for a 2-day event (5 per day)");

    // Verify day indices
    long day0Count = shifts.stream().filter(s -> s.dayIndex() == 0).count();
    long day1Count = shifts.stream().filter(s -> s.dayIndex() == 1).count();
    assertEquals(5, day0Count);
    assertEquals(5, day1Count);
  }

  @Test
  void testListShiftsByEvent() {
    Event event = new Event("test-event-2", "Test Event 2", "Test", 1);
    event.setDate(LocalDate.of(2026, 7, 20));
    eventService.saveEvent(event);

    service.generateShiftsForEvent("test-event-2");

    List<VolunteerShift> shifts = service.listShiftsByEvent("test-event-2");
    assertEquals(5, shifts.size());

    // Verify shifts are sorted by day and time
    for (int i = 0; i < shifts.size() - 1; i++) {
      assertTrue(
          shifts.get(i).startTime().isBefore(shifts.get(i + 1).startTime()),
          "Shifts should be ordered by start time");
    }
  }

  @Test
  void testSetAvailability_Valid() {
    Event event = new Event("test-event-3", "Test Event 3", "Test", 1);
    event.setDate(LocalDate.of(2026, 7, 25));
    eventService.saveEvent(event);

    List<VolunteerShift> shifts = service.generateShiftsForEvent("test-event-3");

    // Select 2 shifts (minimum)
    List<String> selectedShifts = List.of(shifts.get(0).id(), shifts.get(1).id());

    VolunteerAvailability availability =
        service.setAvailability("test-event-3", "user@example.com", "Test User", selectedShifts);

    assertNotNull(availability);
    assertEquals("test-event-3", availability.eventId());
    assertEquals("user@example.com", availability.volunteerUserId());
    assertEquals(2, availability.selectedShiftIds().size());
  }

  @Test
  void testSetAvailability_UpdateExisting() {
    Event event = new Event("test-event-4", "Test Event 4", "Test", 1);
    event.setDate(LocalDate.of(2026, 8, 1));
    eventService.saveEvent(event);

    List<VolunteerShift> shifts = service.generateShiftsForEvent("test-event-4");

    // Set initial availability
    List<String> initialShifts = List.of(shifts.get(0).id(), shifts.get(1).id());
    VolunteerAvailability first =
        service.setAvailability("test-event-4", "user@example.com", "Test User", initialShifts);

    // Update availability
    List<String> updatedShifts =
        List.of(shifts.get(2).id(), shifts.get(3).id(), shifts.get(4).id());
    VolunteerAvailability second =
        service.setAvailability("test-event-4", "user@example.com", "Test User", updatedShifts);

    assertEquals(first.id(), second.id(), "Should update the same availability record");
    assertEquals(3, second.selectedShiftIds().size());
    assertNotEquals(first.updatedAt(), second.updatedAt());
  }

  @Test
  void testSetAvailability_MinSegmentsValidation() {
    Event event = new Event("test-event-5", "Test Event 5", "Test", 1);
    event.setDate(LocalDate.of(2026, 8, 5));
    eventService.saveEvent(event);

    List<VolunteerShift> shifts = service.generateShiftsForEvent("test-event-5");

    // Try to select only 1 shift (below minimum of 2)
    List<String> tooFewShifts = List.of(shifts.get(0).id());

    assertThrows(
        VolunteerShiftService.ValidationException.class,
        () ->
            service.setAvailability("test-event-5", "user@example.com", "Test User", tooFewShifts),
        "Should reject less than 2 segments per day");
  }

  @Test
  void testSetAvailability_MaxSegmentsValidation() {
    Event event = new Event("test-event-6", "Test Event 6", "Test", 1);
    event.setDate(LocalDate.of(2026, 8, 10));
    eventService.saveEvent(event);

    List<VolunteerShift> shifts = service.generateShiftsForEvent("test-event-6");

    // Try to select 5 shifts (above maximum of 4)
    List<String> tooManyShifts = shifts.stream().map(VolunteerShift::id).toList();

    assertThrows(
        VolunteerShiftService.ValidationException.class,
        () ->
            service.setAvailability("test-event-6", "user@example.com", "Test User", tooManyShifts),
        "Should reject more than 4 segments per day");
  }

  @Test
  void testSetAvailability_MultiDayEvent_ValidatesPerDay() {
    Event event = new Event("test-event-7", "Test Event 7", "Test", 2);
    event.setDate(LocalDate.of(2026, 8, 15));
    eventService.saveEvent(event);

    List<VolunteerShift> shifts = service.generateShiftsForEvent("test-event-7");

    // Day 0 shifts (indices 0-4)
    // Day 1 shifts (indices 5-9)
    // Select 2 from day 0, 3 from day 1 (both valid)
    List<String> validMultiDay =
        List.of(
            shifts.get(0).id(),
            shifts.get(1).id(),
            shifts.get(5).id(),
            shifts.get(6).id(),
            shifts.get(7).id());

    VolunteerAvailability availability =
        service.setAvailability("test-event-7", "user@example.com", "Test User", validMultiDay);

    assertNotNull(availability);
    assertEquals(5, availability.selectedShiftIds().size());
  }

  @Test
  void testGetCoverageStats() {
    Event event = new Event("test-event-8", "Test Event 8", "Test", 1);
    event.setDate(LocalDate.of(2026, 8, 20));
    eventService.saveEvent(event);

    List<VolunteerShift> shifts = service.generateShiftsForEvent("test-event-8");

    // No availability yet
    VolunteerShiftService.ShiftCoverageStats stats1 = service.getCoverageStats("test-event-8");
    assertEquals(5, stats1.totalShifts());
    assertEquals(5, stats1.uncoveredShifts());

    // Add some availability
    List<String> selectedShifts = List.of(shifts.get(0).id(), shifts.get(1).id());
    service.setAvailability("test-event-8", "user1@example.com", "User 1", selectedShifts);

    VolunteerShiftService.ShiftCoverageStats stats2 = service.getCoverageStats("test-event-8");
    assertEquals(5, stats2.totalShifts());
    assertEquals(3, stats2.uncoveredShifts());
    assertEquals(1, stats2.coverageByShift().get(shifts.get(0).id()));
    assertEquals(1, stats2.coverageByShift().get(shifts.get(1).id()));
  }

  @Test
  void testListAvailabilitiesByEvent() {
    Event event = new Event("test-event-9", "Test Event 9", "Test", 1);
    event.setDate(LocalDate.of(2026, 8, 25));
    eventService.saveEvent(event);

    List<VolunteerShift> shifts = service.generateShiftsForEvent("test-event-9");

    // Add availability for multiple users
    service.setAvailability(
        "test-event-9",
        "user1@example.com",
        "User 1",
        List.of(shifts.get(0).id(), shifts.get(1).id()));
    service.setAvailability(
        "test-event-9",
        "user2@example.com",
        "User 2",
        List.of(shifts.get(2).id(), shifts.get(3).id()));

    List<VolunteerAvailability> availabilities = service.listAvailabilitiesByEvent("test-event-9");
    assertEquals(2, availabilities.size());
  }
}
