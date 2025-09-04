package io.eventflow.home.now;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import com.scanales.eventflow.service.EventService;
import io.eventflow.time.AppClock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.*;
import java.util.*;
import java.util.stream.*;

@ApplicationScoped
public class NowBoxService {

  @ConfigProperty(name = "nowbox.lookback", defaultValue = "PT30M")
  Duration lookback;

  @ConfigProperty(name = "nowbox.lookahead", defaultValue = "PT60M")
  Duration lookahead;

  @Inject EventService events;
  @Inject AppClock clock;

  public NowBoxView build() {
    Instant nowInstant = clock.now();
    List<NowBoxView.EventNow> list = new ArrayList<>();

    for (Event ev : events.listEvents()) {
      ZoneId tz = ev.getZoneId();
      ZonedDateTime now = ZonedDateTime.ofInstant(nowInstant, tz);
      ZonedDateTime start = ev.getStartDateTime();
      ZonedDateTime end = ev.getEndDateTime();
      if (start == null || end == null) continue;
      if (now.isBefore(start) || !now.isBefore(end)) continue; // only active events

      List<Talk> agenda = ev.getAgenda();
      if (agenda == null || agenda.isEmpty()) continue;

      List<Talk> currents = agenda.stream()
          .filter(t -> isCurrent(t, ev, now, tz))
          .sorted(Comparator.comparing(t -> startAt(t, ev, tz)))
          .collect(Collectors.toList());

      List<Talk> past = agenda.stream()
          .filter(t -> isPast(t, ev, now, tz))
          .sorted(Comparator.comparing((Talk t) -> endAt(t, ev, tz)).reversed())
          .collect(Collectors.toList());

      List<Talk> future = agenda.stream()
          .filter(t -> isFuture(t, ev, now, tz))
          .sorted(Comparator.comparing(t -> startAt(t, ev, tz)))
          .collect(Collectors.toList());

      NowBoxView.EventNow en = new NowBoxView.EventNow();
      en.eventId = ev.getId();
      en.eventName = ev.getTitle();
      en.eventTimezone = tz.getId();
      en.agendaUrl = "/event/" + ev.getId();
      if (!past.isEmpty()) en.last = toView(ev, past.get(0), tz);
      if (!currents.isEmpty()) en.current = toView(ev, currents.get(0), tz);
      if (!future.isEmpty()) en.next = toView(ev, future.get(0), tz);

      if (en.last != null || en.current != null || en.next != null) list.add(en);
    }

    NowBoxView view = new NowBoxView();
    view.events = list.stream()
        .sorted(Comparator
            .comparing((NowBoxView.EventNow e) -> e.current == null)
            .thenComparing(e -> e.current != null ? e.current.start :
                (e.next != null ? e.next.start : (e.last != null ? e.last.end : ZonedDateTime.ofInstant(nowInstant, ZoneId.of("UTC")))))
        ).collect(Collectors.toList());
    return view;
  }

  private boolean isCurrent(Talk t, Event ev, ZonedDateTime now, ZoneId tz) {
    ZonedDateTime start = startAt(t, ev, tz);
    ZonedDateTime end = endAt(t, ev, tz);
    return start != null && end != null && !now.isBefore(start) && now.isBefore(end);
  }

  private boolean isPast(Talk t, Event ev, ZonedDateTime now, ZoneId tz) {
    ZonedDateTime end = endAt(t, ev, tz);
    return end != null && !now.isBefore(end) && Duration.between(end, now).compareTo(lookback) <= 0;
  }

  private boolean isFuture(Talk t, Event ev, ZonedDateTime now, ZoneId tz) {
    ZonedDateTime start = startAt(t, ev, tz);
    return start != null && start.isAfter(now) && Duration.between(now, start).compareTo(lookahead) <= 0;
  }

  private ZonedDateTime startAt(Talk t, Event ev, ZoneId tz) {
    if (ev.getDate() == null || t.getStartTime() == null) return null;
    LocalDate date = ev.getDate().plusDays(Math.max(0, t.getDay() - 1));
    return ZonedDateTime.of(date, t.getStartTime(), tz);
  }

  private ZonedDateTime endAt(Talk t, Event ev, ZoneId tz) {
    ZonedDateTime start = startAt(t, ev, tz);
    return start != null ? start.plusMinutes(t.getDurationMinutes()) : null;
  }

  private NowBoxView.ActivityView toView(Event ev, Talk t, ZoneId tz) {
    NowBoxView.ActivityView v = new NowBoxView.ActivityView();
    v.id = t.getId();
    v.title = t.getName();
    v.type = t.isBreak() ? "break" : "talk";
    v.start = startAt(t, ev, tz);
    v.end = endAt(t, ev, tz);
    v.room = t.getLocation();
    v.speaker = t.getSpeakerNames();
    v.detailUrl = t.isBreak()
        ? "/event/" + ev.getId() + "#break-" + t.getId()
        : "/event/" + ev.getId() + "/talk/" + t.getId();
    return v;
  }
}
