package com.scanales.eventflow.eventops;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public record EventOperationsStateSnapshot(
    @JsonProperty("schema_version") int schemaVersion,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("staff_assignments") Map<String, EventStaffAssignment> staffAssignments,
    Map<String, EventSpace> spaces,
    @JsonProperty("space_shifts") Map<String, EventSpaceResponsibleShift> spaceShifts,
    Map<String, EventSpaceActivity> activities) {

  public static final int SCHEMA_VERSION = 1;

  public EventOperationsStateSnapshot {
    staffAssignments = staffAssignments == null ? Map.of() : Map.copyOf(staffAssignments);
    spaces = spaces == null ? Map.of() : Map.copyOf(spaces);
    spaceShifts = spaceShifts == null ? Map.of() : Map.copyOf(spaceShifts);
    activities = activities == null ? Map.of() : Map.copyOf(activities);
  }

  public static EventOperationsStateSnapshot empty() {
    return new EventOperationsStateSnapshot(
        SCHEMA_VERSION, Instant.now(), Map.of(), Map.of(), Map.of(), Map.of());
  }
}

