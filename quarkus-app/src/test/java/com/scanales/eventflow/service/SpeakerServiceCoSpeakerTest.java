package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.scanales.eventflow.model.Speaker;
import com.scanales.eventflow.model.Talk;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class SpeakerServiceCoSpeakerTest {

    @Inject
    SpeakerService speakerService;

    @AfterEach
    public void cleanup() {
        speakerService.deleteSpeaker("main");
        speakerService.deleteSpeaker("co");
    }

    @Test
    public void savesCoSpeakerAndMainSpeaker() {
        Speaker main = new Speaker("main", "Main");
        Speaker co = new Speaker("co", "Co");
        speakerService.saveSpeaker(main);
        speakerService.saveSpeaker(co);

        Talk talk = new Talk("talk1", "Test Talk");
        talk.setDurationMinutes(30);
        talk.setSpeakers(List.of(co));

        speakerService.saveTalk("main", talk);

        Talk stored = speakerService.getTalk("main", "talk1");
        assertNotNull(stored);
        assertEquals(2, stored.getSpeakers().size());
        assertEquals("main", stored.getSpeakers().get(0).getId());
        assertEquals("co", stored.getSpeakers().get(1).getId());

        Talk coStored = speakerService.getTalk("co", "talk1");
        assertNotNull(coStored);
        assertEquals("main", coStored.getSpeakers().get(0).getId());
        assertEquals("co", coStored.getSpeakers().get(1).getId());
    }

    @Test
    public void deletesTalkFromCoSpeaker() {
        Speaker main = new Speaker("main", "Main");
        Speaker co = new Speaker("co", "Co");
        speakerService.saveSpeaker(main);
        speakerService.saveSpeaker(co);

        Talk talk = new Talk("talk1", "Test Talk");
        talk.setDurationMinutes(30);
        talk.setSpeakers(List.of(co));

        speakerService.saveTalk("main", talk);
        speakerService.deleteTalk("main", "talk1");

        assertNull(speakerService.getTalk("main", "talk1"));
        assertNull(speakerService.getTalk("co", "talk1"));
    }
}
