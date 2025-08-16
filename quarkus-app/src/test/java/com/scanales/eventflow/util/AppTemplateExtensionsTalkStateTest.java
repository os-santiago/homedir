package com.scanales.eventflow.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

public class AppTemplateExtensionsTalkStateTest {

  @Test
  void talkStateUsesEventTimezone() {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
    ZonedDateTime talkStartTokyo = nowUtc.minusMinutes(30).withZoneSameInstant(tokyo);

    Event event = new Event();
    event.setDate(talkStartTokyo.toLocalDate());
    event.setTimezone(tokyo.getId());

    Talk talk = new Talk("id", "name");
    talk.setStartTime(talkStartTokyo.toLocalTime());
    talk.setDurationMinutes(10);

    String state = AppTemplateExtensions.talkState(talk, event);
    assertEquals("Finalizada", state);
  }
}
