package com.scanales.eventflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

public class TalkStatusTest {

    @Test
    public void testStatuses() {
        Talk talk = new Talk("t1", "Test");
        talk.setDay(1);
        talk.setStartTime(LocalTime.of(10, 0));
        talk.setDurationMinutes(60);
        LocalDate eventStart = LocalDate.of(2024, 1, 1);

        assertEquals(Talk.Status.ON_TIME,
                talk.getStatus(eventStart, LocalDateTime.of(2024, 1, 1, 9, 0)));
        assertEquals(Talk.Status.SOON,
                talk.getStatus(eventStart, LocalDateTime.of(2024, 1, 1, 9, 45)));
        assertEquals(Talk.Status.IN_PROGRESS,
                talk.getStatus(eventStart, LocalDateTime.of(2024, 1, 1, 10, 15)));
        assertEquals(Talk.Status.FINISHED,
                talk.getStatus(eventStart, LocalDateTime.of(2024, 1, 1, 11, 5)));
    }
}
