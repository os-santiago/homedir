package com.scanales.homedir.volunteers;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * Represents a 2-hour shift segment for volunteer coverage during an event. Each shift has a
 * specific day index, start/end times, and capacity.
 */
public record VolunteerShift(
    String id,
    @JsonProperty("event_id") String eventId,
    @JsonProperty("day_index") int dayIndex,
    @JsonProperty("start_time") LocalDateTime startTime,
    @JsonProperty("end_time") LocalDateTime endTime,
    @JsonProperty("max_volunteers") int maxVolunteers,
    String label) {

  public static final int SHIFT_DURATION_HOURS = 2;

  public VolunteerShift {
    if (dayIndex < 0) {
      throw new IllegalArgumentException("dayIndex must be >= 0");
    }
    if (maxVolunteers < 1) {
      throw new IllegalArgumentException("maxVolunteers must be >= 1");
    }
    if (startTime == null || endTime == null) {
      throw new IllegalArgumentException("startTime and endTime are required");
    }
    if (!endTime.isAfter(startTime)) {
      throw new IllegalArgumentException("endTime must be after startTime");
    }
  }
}
