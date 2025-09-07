package io.eventflow.home.now;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

/** View model for the "Ocurriendo ahora" box. */
public class NowBoxView {
  public List<EventNow> events = Collections.emptyList();

  public static class EventNow {
    public String eventId;
    public String eventName;
    public String eventTimezone; // optional
    public ActivityView last;    // may be null
    public ActivityView current; // may be null
    public ActivityView next;    // may be null
    public String agendaUrl;     // e.g. /events/{eventId}
  }

  public static class ActivityView {
    public String id;          // talk or break id
    public String title;
    public String type;        // "talk" | "break"
    public ZonedDateTime start;
    public ZonedDateTime end;
    public String detailUrl;   // link to detail
    public String room;        // optional
    public String speaker;     // optional for talks
  }
}
