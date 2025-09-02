package io.eventflow.time;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Holds an overridable instant for simulation purposes. */
@ApplicationScoped
@Named("simClock")
public class SimulatedClock {
  private final AtomicReference<Instant> override = new AtomicReference<>();

  public boolean isActive() {
    return override.get() != null;
  }

  public void set(Instant instant) {
    override.set(instant);
  }

  public void clear() {
    override.set(null);
  }

  public Instant now() {
    return Optional.ofNullable(override.get()).orElseGet(Instant::now);
  }

  public ZonedDateTime now(ZoneId zone) {
    return ZonedDateTime.ofInstant(now(), zone);
  }
}
