package io.eventflow.notifications.global;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import java.time.*;
import java.util.*;

/** Utility to compute which notifications would be emitted at a given instant. */
public class SimulationEngine {

  public static List<GlobalNotification> plan(
      NotificationSimulationResource.SimRequest req,
      EventService events,
      Duration upcomingWin,
      Duration endingWin) {
    List<GlobalNotification> out = new ArrayList<>();
    Instant pivot = req.pivot;
    List<String> states =
        req.states != null ? req.states : List.of("UPCOMING", "STARTED", "ENDING_SOON", "FINISHED");
    for (Event ev : events.listEvents()) {
      if (req.eventId != null && !req.eventId.equals(ev.getId())) continue;
      ZoneId tz = ev.getZoneId();
      ZonedDateTime now = ZonedDateTime.ofInstant(pivot, tz);
      if (req.includeEvent) {
        evalEvent(ev, now, upcomingWin, endingWin, states, pivot, out);
      }
      if ((req.includeTalks || req.includeBreaks) && ev.getAgenda() != null) {
        for (Talk t : ev.getAgenda()) {
          if (t.getStartTime() == null) continue;
          boolean isBreak = t.isBreak();
          if (isBreak && !req.includeBreaks) continue;
          if (!isBreak && !req.includeTalks) continue;
          ZonedDateTime start =
              ZonedDateTime.of(ev.getDate().plusDays(t.getDay() - 1), t.getStartTime(), tz);
          ZonedDateTime end = start.plusMinutes(t.getDurationMinutes());
          evalSlot(
              ev,
              t,
              isBreak ? "break" : "talk",
              start,
              end,
              now,
              upcomingWin,
              endingWin,
              states,
              pivot,
              out);
        }
      }
    }
    return out;
  }

  private static void evalEvent(
      Event ev,
      ZonedDateTime now,
      Duration upcomingWin,
      Duration endingWin,
      List<String> states,
      Instant pivot,
      List<GlobalNotification> out) {
    ZonedDateTime start = ev.getStartDateTime();
    ZonedDateTime end = ev.getEndDateTime();
    if (start == null || end == null) return;
    if (states.contains("UPCOMING") && inWindow(now, start.minus(upcomingWin), start)) {
      out.add(create("event", ev.getId(), null, ev.getTitle(), "UPCOMING", start, pivot));
    }
    if (states.contains("STARTED") && !now.isBefore(start) && now.isBefore(end)) {
      out.add(create("event", ev.getId(), null, ev.getTitle(), "STARTED", start, pivot));
    }
    if (states.contains("ENDING_SOON") && inWindow(now, end.minus(endingWin), end)) {
      out.add(create("event", ev.getId(), null, ev.getTitle(), "ENDING_SOON", end, pivot));
    }
    if (states.contains("FINISHED") && !now.isBefore(end)) {
      out.add(create("event", ev.getId(), null, ev.getTitle(), "FINISHED", end, pivot));
    }
  }

  private static void evalSlot(
      Event ev,
      Talk t,
      String category,
      ZonedDateTime start,
      ZonedDateTime end,
      ZonedDateTime now,
      Duration upcomingWin,
      Duration endingWin,
      List<String> states,
      Instant pivot,
      List<GlobalNotification> out) {
    if (states.contains("UPCOMING") && inWindow(now, start.minus(upcomingWin), start)) {
      out.add(create(category, ev.getId(), t.getId(), t.getName(), "UPCOMING", start, pivot));
    }
    if (states.contains("STARTED") && !now.isBefore(start) && now.isBefore(end)) {
      out.add(create(category, ev.getId(), t.getId(), t.getName(), "STARTED", start, pivot));
    }
    if (states.contains("ENDING_SOON") && inWindow(now, end.minus(endingWin), end)) {
      out.add(create(category, ev.getId(), t.getId(), t.getName(), "ENDING_SOON", end, pivot));
    }
    if (states.contains("FINISHED") && !now.isBefore(end)) {
      out.add(create(category, ev.getId(), t.getId(), t.getName(), "FINISHED", end, pivot));
    }
  }

  private static boolean inWindow(ZonedDateTime now, ZonedDateTime from, ZonedDateTime to) {
    return !now.isBefore(from) && now.isBefore(to);
  }

  private static GlobalNotification create(
      String category,
      String eventId,
      String talkId,
      String message,
      String type,
      ZonedDateTime edge,
      Instant pivot) {
    GlobalNotification n = new GlobalNotification();
    n.type = type;
    n.category = category;
    n.eventId = eventId;
    n.talkId = talkId;
    n.message = message;
    n.title = titleFor(category, type);
    n.createdAt = pivot.toEpochMilli();
    n.dedupeKey =
        String.format(
            "global:%s:%s:%s:%d",
            category, talkId != null ? talkId : eventId, type, edge.toEpochSecond());
    return n;
  }

  private static String titleFor(String category, String type) {
    String base =
        switch (category) {
          case "event" -> "El evento";
          case "talk" -> "La charla";
          case "break" -> "Break";
          default -> "Aviso";
        };
    return switch (type) {
      case "UPCOMING" -> base + " comienza pronto";
      case "STARTED" -> base + " en curso";
      case "ENDING_SOON" -> base + " por finalizar";
      case "FINISHED" -> base + " finalizado";
      default -> base;
    };
  }
}
