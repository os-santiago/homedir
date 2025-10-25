package io.eventflow.time;

import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Default implementation delegating to system clock unless a simulation is active. */
@ApplicationScoped
public class SystemAppClock implements AppClock {
  @Inject
  @Nullable
  @Named("simClock")
  SimulatedClock sim;

  @Override
  public Instant now() {
    return sim != null && sim.isActive() ? sim.now() : Instant.now();
  }

  @Override
  public ZonedDateTime now(ZoneId zone) {
    return sim != null && sim.isActive() ? sim.now(zone) : ZonedDateTime.now(zone);
  }
}
