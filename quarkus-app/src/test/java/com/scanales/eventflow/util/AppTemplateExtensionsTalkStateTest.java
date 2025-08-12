package com.scanales.eventflow.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;

public class AppTemplateExtensionsTalkStateTest {

    @Test
    void talkStateUsesEventTimezone() {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
        ZonedDateTime nowTokyo = nowUtc.withZoneSameInstant(tokyo);

        Event event = new Event();
        event.setDate(nowTokyo.toLocalDate());
        event.setTimezone(tokyo.getId());

        Talk talk = new Talk("id", "name");
        talk.setStartTime(nowTokyo.minusMinutes(30).toLocalTime());
        talk.setDurationMinutes(10);

        String state = AppTemplateExtensions.talkState(talk, event);
        assertEquals("Finalizada", state);
    }
}
