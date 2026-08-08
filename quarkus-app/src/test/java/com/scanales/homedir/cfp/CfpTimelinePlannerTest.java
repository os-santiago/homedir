package com.scanales.homedir.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.model.Event;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CfpTimelinePlannerTest {

  @Test
  void keepsCfpWindowOpenDuringSubmissionPeriodAndAdvancesToEvaluationAfterClose() {
    ZoneId zone = ZoneId.of("America/Santiago");
    Event event = new Event("devopsdays-santiago-2026", "DevOpsDays Santiago 2026", "CFP timeline");
    event.setDate(LocalDate.of(2026, 10, 10));
    event.setDays(2);
    event.setTimezone(zone.getId());

    Instant opensAt = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant();
    Instant closesAt = LocalDate.of(2026, 4, 1).atStartOfDay(zone).toInstant();
    Instant duringCfp = LocalDate.of(2026, 2, 1).atStartOfDay(zone).toInstant();
    Instant afterClose = LocalDate.of(2026, 4, 2).atStartOfDay(zone).toInstant();

    CfpTimelineView openTimeline =
        CfpTimelinePlanner.build(event, opensAt, closesAt, Locale.ENGLISH, duringCfp).orElseThrow();
    assertTrue(openTimeline.cfpWindowOpen());
    assertNotNull(openTimeline.activeStage());
    assertEquals("cfp", openTimeline.activeStage().key());
    assertTrue(openTimeline.activeStage().active());

    CfpTimelineView closedTimeline =
        CfpTimelinePlanner.build(event, opensAt, closesAt, Locale.ENGLISH, afterClose)
            .orElseThrow();
    assertFalse(closedTimeline.cfpWindowOpen());
    assertNotNull(closedTimeline.activeStage());
    assertEquals("evaluation", closedTimeline.activeStage().key());

    List<CfpTimelineStageView> stages = closedTimeline.stages();
    assertEquals(5, stages.size());
    assertEquals("cfp", stages.get(0).key());
    assertEquals("evaluation", stages.get(1).key());
    assertEquals("results", stages.get(2).key());
    assertEquals("presentations", stages.get(3).key());
    assertEquals("event", stages.get(4).key());
    assertTrue(stages.stream().allMatch(stage -> stage.flexDays() > 0));
    assertTrue(stages.get(1).active());
    assertFalse(stages.get(0).active());
    assertFalse(stages.get(2).active());
    assertFalse(stages.get(3).active());
    assertFalse(stages.get(4).active());
  }

  @Test
  void derivesFallbackDatesFromTheEventWhenCfpWindowIsMissing() {
    ZoneId zone = ZoneId.of("America/Santiago");
    Event event = new Event("event-1", "Event 1", "No explicit CFP window");
    event.setDate(LocalDate.of(2026, 9, 20));
    event.setDays(2);
    event.setTimezone(zone.getId());

    Instant duringFallbackWindow = LocalDate.of(2026, 7, 1).atStartOfDay(zone).toInstant();

    CfpTimelineView timeline =
        CfpTimelinePlanner.build(
                event, null, null, Locale.forLanguageTag("es"), duringFallbackWindow)
            .orElseThrow();

    assertEquals(5, timeline.stages().size());
    assertFalse(timeline.fromLabel().isBlank());
    assertFalse(timeline.toLabel().isBlank());
    assertTrue(timeline.cfpWindowOpen());
    assertNotNull(timeline.activeStage());
    assertEquals("cfp", timeline.activeStage().key());
  }

  @Test
  void endedIsFalseOnFinalEventDayAndTrueTheDayAfter() {
    ZoneId zone = ZoneId.of("America/Santiago");
    Event event = new Event("event-final-day", "Final Day Event", "ended boundary");
    event.setDate(LocalDate.of(2026, 11, 1));
    event.setDays(2);
    event.setTimezone(zone.getId());

    Instant cfpOpens = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant();
    Instant cfpCloses = LocalDate.of(2026, 4, 1).atStartOfDay(zone).toInstant();

    Instant finalEventDay = LocalDate.of(2026, 11, 2).atStartOfDay(zone).toInstant();
    CfpTimelineView onFinalDay =
        CfpTimelinePlanner.build(event, cfpOpens, cfpCloses, Locale.ENGLISH, finalEventDay)
            .orElseThrow();
    assertFalse(onFinalDay.ended(), "ended must be false on the final event day");
    assertNotNull(onFinalDay.activeStage());
    assertEquals("event", onFinalDay.activeStage().key());

    Instant dayAfter = LocalDate.of(2026, 11, 3).atStartOfDay(zone).toInstant();
    CfpTimelineView afterEvent =
        CfpTimelinePlanner.build(event, cfpOpens, cfpCloses, Locale.ENGLISH, dayAfter)
            .orElseThrow();
    assertTrue(afterEvent.ended(), "ended must be true the day after the event");
    assertNull(afterEvent.activeStage());
  }

  @Test
  void endedStaysConsistentWithCappedEventStageForEventsLongerThanTwoDays() {
    ZoneId zone = ZoneId.of("America/Santiago");
    Event event = new Event("event-long", "Long Event", "capped timeline consistency");
    event.setDate(LocalDate.of(2026, 12, 1));
    event.setDays(5);
    event.setTimezone(zone.getId());

    Instant cfpOpens = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant();
    Instant cfpCloses = LocalDate.of(2026, 4, 1).atStartOfDay(zone).toInstant();

    CfpTimelineStageView eventStage =
        CfpTimelinePlanner.build(event, cfpOpens, cfpCloses, Locale.ENGLISH, cfpOpens)
            .orElseThrow()
            .stages()
            .stream()
            .filter(s -> "event".equals(s.key()))
            .findFirst()
            .orElseThrow();
    assertEquals(
        2, eventStage.flexDays(), "event stage must be capped to two days even for a 5-day event");

    Instant duringCappedWindow = LocalDate.of(2026, 12, 2).atStartOfDay(zone).toInstant();
    CfpTimelineView onCappedFinalDay =
        CfpTimelinePlanner.build(event, cfpOpens, cfpCloses, Locale.ENGLISH, duringCappedWindow)
            .orElseThrow();
    assertFalse(
        onCappedFinalDay.ended(),
        "ended must be false while the capped event stage is still active");
    assertNotNull(onCappedFinalDay.activeStage());
    assertEquals("event", onCappedFinalDay.activeStage().key());

    Instant afterCappedWindow = LocalDate.of(2026, 12, 3).atStartOfDay(zone).toInstant();
    CfpTimelineView pastCappedEnd =
        CfpTimelinePlanner.build(event, cfpOpens, cfpCloses, Locale.ENGLISH, afterCappedWindow)
            .orElseThrow();
    assertTrue(
        pastCappedEnd.ended(),
        "ended must be true once nowDate passes the capped eventEnd, consistent with the stage");
    assertNull(pastCappedEnd.activeStage());
  }
}
