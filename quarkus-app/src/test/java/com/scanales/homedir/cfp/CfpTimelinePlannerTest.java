package com.scanales.homedir.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.model.Event;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CfpTimelinePlannerTest {

  @Test
  void buildsProportionalTimelineWithMilestonesAndEventWindow() {
    ZoneId zone = ZoneId.of("America/Santiago");
    Event event = new Event("devopsdays-santiago-2026", "DevOpsDays Santiago 2026", "CFP timeline");
    event.setDate(LocalDate.of(2026, 10, 10));
    event.setDays(2);
    event.setTimezone(zone.getId());

    Instant opensAt = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant();
    Instant closesAt = LocalDate.of(2026, 4, 1).atStartOfDay(zone).toInstant();
    Instant now = LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant();

    CfpTimelineView timeline =
        CfpTimelinePlanner.build(event, opensAt, closesAt, Locale.ENGLISH, now).orElseThrow();

    List<CfpTimelineStageView> stages = timeline.stages();
    assertEquals(5, stages.size());
    assertEquals("cfp", stages.get(0).key());
    assertEquals("evaluation", stages.get(1).key());
    assertEquals("results", stages.get(2).key());
    assertEquals("presentations", stages.get(3).key());
    assertEquals("event", stages.get(4).key());
    assertTrue(stages.stream().allMatch(stage -> stage.flexDays() > 0));
    assertTrue(stages.get(3).active());
    assertFalse(stages.get(0).active());
    assertFalse(stages.get(1).active());
    assertFalse(stages.get(2).active());
    assertFalse(stages.get(4).active());
  }

  @Test
  void usesFallbackDatesWhenCfpWindowIsMissing() {
    ZoneId zone = ZoneId.of("America/Santiago");
    Event event = new Event("event-1", "Event 1", "No explicit CFP window");
    event.setDate(LocalDate.of(2026, 9, 20));
    event.setDays(2);
    event.setTimezone(zone.getId());

    CfpTimelineView timeline =
        CfpTimelinePlanner
            .build(event, null, null, Locale.forLanguageTag("es"), ZonedDateTime.now(zone).toInstant())
            .orElseThrow();

    assertEquals(5, timeline.stages().size());
    assertFalse(timeline.fromLabel().isBlank());
    assertFalse(timeline.toLabel().isBlank());
  }
}
