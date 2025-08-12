package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.scanales.eventflow.model.Event;
import com.scanales.eventflow.model.Talk;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class EventServiceOverlapTest {

    @Inject
    EventService eventService;

    private static final String EVENT_ID = "e-overlap";

    @BeforeEach
    public void setup() {
        Event event = new Event(EVENT_ID, "Event", "desc");
        Talk t1 = new Talk("t1", "Talk 1");
        t1.setLocation("room");
        t1.setDay(1);
        t1.setStartTime(LocalTime.of(9, 0));
        t1.setDurationMinutes(30); // ends 09:30
        event.getAgenda().add(t1);
        eventService.saveEvent(event);
    }

    @AfterEach
    public void cleanup() {
        eventService.deleteEvent(EVENT_ID);
    }

    @Test
    public void touchingTalksDoNotOverlap() {
        Talk t2 = new Talk("t2", "Talk 2");
        t2.setLocation("room");
        t2.setDay(1);
        t2.setStartTime(LocalTime.of(9, 30)); // starts when previous ends
        t2.setDurationMinutes(30);
        assertNull(eventService.findOverlap(EVENT_ID, t2));
    }

    @Test
    public void overlappingTalksAreDetected() {
        Talk t2 = new Talk("t3", "Talk 3");
        t2.setLocation("room");
        t2.setDay(1);
        t2.setStartTime(LocalTime.of(9, 25)); // starts before previous ends
        t2.setDurationMinutes(30);
        assertNotNull(eventService.findOverlap(EVENT_ID, t2));
    }
}
