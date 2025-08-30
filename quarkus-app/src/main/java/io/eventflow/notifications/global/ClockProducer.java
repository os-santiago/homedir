package io.eventflow.notifications.global;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;

/** Provides a default system clock for injection. */
@Singleton
public class ClockProducer {
  @Produces
  public Clock clock() {
    return Clock.systemUTC();
  }
}
