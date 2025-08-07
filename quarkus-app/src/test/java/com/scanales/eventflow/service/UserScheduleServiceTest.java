package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UserScheduleService} that avoid hitting private HTTP endpoints.
 */
public class UserScheduleServiceTest {

    @Test
    public void updateDetailsAndSummary() {
        UserScheduleService svc = new UserScheduleService();
        String user = "user@example.com";

        assertTrue(svc.addTalkForUser(user, "t1"));
        assertTrue(svc.updateTalk(user, "t1", true, 5, Set.of("⭐ Relevante para mi trabajo")));

        UserScheduleService.TalkDetails details = svc.getTalkDetailsForUser(user).get("t1");
        assertNotNull(details);
        assertTrue(details.attended);
        assertEquals(5, details.rating);
        assertTrue(details.motivations.contains("⭐ Relevante para mi trabajo"));

        UserScheduleService.Summary summary = svc.getSummaryForUser(user);
        assertEquals(1, summary.total());
        assertEquals(1, summary.attended());
        assertEquals(1, summary.rated());
    }

    @Test
    public void removeTalk() {
        UserScheduleService svc = new UserScheduleService();
        String user = "user@example.com";

        assertTrue(svc.addTalkForUser(user, "t1"));
        assertTrue(svc.removeTalkForUser(user, "t1"));
        assertFalse(svc.removeTalkForUser(user, "t1"));

        UserScheduleService.Summary summary = svc.getSummaryForUser(user);
        assertEquals(0, summary.total());
        assertEquals(0, summary.attended());
        assertEquals(0, summary.rated());
    }

    @Test
    public void ignoreNullUser() {
        UserScheduleService svc = new UserScheduleService();

        assertFalse(svc.addTalkForUser(null, "t1"));
        assertTrue(svc.getTalksForUser(null).isEmpty());
        assertTrue(svc.getTalkDetailsForUser(null).isEmpty());
        assertEquals(0, svc.getSummaryForUser(null).total());
    }
}

