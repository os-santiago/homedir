package io.eventflow.notifications.global;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Evaluates break slots to emit global notifications. */
@ApplicationScoped
public class BreakStateEvaluator {

  @ConfigProperty(name = "notifications.upcoming.window", defaultValue = "PT5M")
  Duration upcomingWin;

  @ConfigProperty(name = "notifications.endingSoon.window", defaultValue = "PT5M")
  Duration endingWin;

  @Inject GlobalNotificationService global;
  @Inject EventService events;
  @Inject Clock clock;

  @Scheduled(every = "{notifications.scheduler.interval}")
  void tick() {
    if (!GlobalNotificationConfig.enabled) return;
    ZonedDateTime now = ZonedDateTime.now(clock);
    for (Event ev : events.listEvents()) {
      ZoneId tz = ev.getZoneId();
      for (Talk t : ev.getAgenda()) {
        if (!t.isBreak() || t.getStartTime() == null) continue;
        if (ev.getDate() == null) continue;
        ZonedDateTime start = ZonedDateTime.of(
            ev.getDate().plusDays(t.getDay() - 1), t.getStartTime(), tz);
        ZonedDateTime end = start.plusMinutes(t.getDurationMinutes());
        ZonedDateTime nowTz = now.withZoneSameInstant(tz);
        if (inWindow(nowTz, start.minus(upcomingWin), start)) {
          enqueue(ev, t, "UPCOMING", start);
        }
        if (!nowTz.isBefore(start) && nowTz.isBefore(end)) {
          enqueue(ev, t, "STARTED", start);
        }
        if (inWindow(nowTz, end.minus(endingWin), end)) {
          enqueue(ev, t, "ENDING_SOON", end);
        }
        if (!nowTz.isBefore(end)) {
          enqueue(ev, t, "FINISHED", end);
        }
      }
    }
  }

  private boolean inWindow(ZonedDateTime now, ZonedDateTime from, ZonedDateTime to) {
    return !now.isBefore(from) && now.isBefore(to);
  }

  private void enqueue(Event ev, Talk t, String type, ZonedDateTime edge) {
    String dedupeKey = String.format(
        "global:break:%s:%s:%d", t.getId(), type, edge.toEpochSecond());
    GlobalNotification n = new GlobalNotification();
    n.type = type;
    n.category = "break";
    n.eventId = ev.getId();
    n.talkId = t.getId();
    n.title = switch (type) {
      case "UPCOMING" -> "Break comienza pronto";
      case "STARTED" -> "Break en curso";
      case "ENDING_SOON" -> "Break por finalizar";
      case "FINISHED" -> "Break finalizado";
      default -> "Break";
    };
    n.message = t.getName();
    n.createdAt = Instant.now(clock).toEpochMilli();
    n.dedupeKey = dedupeKey;
    global.enqueue(n);
  }
}
