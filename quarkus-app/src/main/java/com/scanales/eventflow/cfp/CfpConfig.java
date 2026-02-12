package com.scanales.eventflow.cfp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Small persisted config for the CFP module (kept out of the submissions file). */
public record CfpConfig(
    @JsonProperty("max_submissions_per_user_per_event") int maxSubmissionsPerUserPerEvent,
    @JsonProperty("testing_mode_enabled") boolean testingModeEnabled,
    @JsonProperty("updated_at") Instant updatedAt) {

  public static CfpConfig defaults(int maxSubmissionsPerUserPerEvent, boolean testingModeEnabled) {
    return new CfpConfig(maxSubmissionsPerUserPerEvent, testingModeEnabled, Instant.now());
  }

  public CfpConfig withMaxSubmissionsPerUserPerEvent(int value) {
    return new CfpConfig(value, testingModeEnabled, Instant.now());
  }

  public CfpConfig withTestingModeEnabled(boolean enabled) {
    return new CfpConfig(maxSubmissionsPerUserPerEvent, enabled, Instant.now());
  }
}

