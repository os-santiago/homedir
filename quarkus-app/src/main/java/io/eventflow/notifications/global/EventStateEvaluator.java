package io.eventflow.notifications.global;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.service.EventService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.*;
import io.eventflow.time.AppClock;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Evaluates event state transitions to emit global notifications. */
@ApplicationScoped
public class EventStateEvaluator {

  @ConfigProperty(name = "notifications.upcoming.window", defaultValue = "PT5M")
  Duration upcomingWin;

  @ConfigProperty(name = "notifications.endingSoon.window", defaultValue = "PT5M")
  Duration endingWin;

  @Inject GlobalNotificationService global;
  @Inject EventService events;
  @Inject AppClock clock;

  @Scheduled(every = "{notifications.scheduler.interval}")
  void tick() {
    if (!GlobalNotificationConfig.enabled) return;
    ZonedDateTime now = clock.now(ZoneId.systemDefault());
    for (Event ev : events.listEvents()) {
      ZonedDateTime start = ev.getStartDateTime();
      ZonedDateTime end = ev.getEndDateTime();
      if (start == null || end == null) continue;
      ZoneId tz = ev.getZoneId();
      ZonedDateTime nowTz = now.withZoneSameInstant(tz);
      if (nowTz.toLocalDate().isAfter(end.toLocalDate())) continue;
      // UPCOMING
      if (inWindow(nowTz, start.minus(upcomingWin), start)) {
        enqueue(ev, "UPCOMING", start);
      }
      // STARTED
      if (!nowTz.isBefore(start) && nowTz.isBefore(end)) {
        enqueue(ev, "STARTED", start);
      }
      // ENDING_SOON
      if (inWindow(nowTz, end.minus(endingWin), end)) {
        enqueue(ev, "ENDING_SOON", end);
      }
      // FINISHED
      if (!nowTz.isBefore(end)) {
        enqueue(ev, "FINISHED", end);
      }
    }
  }

  private boolean inWindow(ZonedDateTime now, ZonedDateTime from, ZonedDateTime to) {
    return !now.isBefore(from) && now.isBefore(to);
  }

  private void enqueue(Event ev, String type, ZonedDateTime edge) {
    String dedupeKey =
        String.format("global:event:%s:%s:%d", ev.getId(), type, edge.toEpochSecond());
    GlobalNotification n = new GlobalNotification();
    n.type = type;
    n.category = "event";
    n.eventId = ev.getId();
    n.title =
        switch (type) {
          case "UPCOMING" -> "El evento comienza pronto";
          case "STARTED" -> "El evento está en curso";
          case "ENDING_SOON" -> "El evento está por finalizar";
          case "FINISHED" -> "El evento ha finalizado";
          default -> "Evento";
        };
    n.message = ev.getTitle();
    n.createdAt = clock.now().toEpochMilli();
    n.dedupeKey = dedupeKey;
    global.enqueue(n);
  }
}
