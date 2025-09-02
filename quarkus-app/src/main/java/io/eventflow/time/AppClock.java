package io.eventflow.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Simple abstraction over the system clock allowing simulation. */
public interface AppClock {
  /** Returns current instant. */
  Instant now();

  /** Returns current date time in given zone. */
  ZonedDateTime now(ZoneId zone);
}
