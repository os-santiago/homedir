package com.scanales.homedir.volunteers;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Tracks a volunteer's selected availability for shifts within an event.
 * Enforces business rules: minimum 2 segments per day, maximum 4 segments per day.
 */
public record VolunteerAvailability(
    String id,
    @JsonProperty("event_id") String eventId,
    @JsonProperty("volunteer_user_id") String volunteerUserId,
    @JsonProperty("volunteer_name") String volunteerName,
    @JsonProperty("selected_shift_ids") List<String> selectedShiftIds,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt) {

  public VolunteerAvailability {
    if (selectedShiftIds == null) {
      selectedShiftIds = List.of();
    } else {
      selectedShiftIds = List.copyOf(selectedShiftIds);
    }
  }

  public static final int MIN_SEGMENTS_PER_DAY = 2;
  public static final int MAX_SEGMENTS_PER_DAY = 4;
}
