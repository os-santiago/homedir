package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Year;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link UserScheduleService} that avoid hitting private HTTP endpoints.
 */
public class UserScheduleServiceTest {

    @TempDir
    Path tempDir;

    private UserScheduleService newService() throws Exception {
        System.setProperty("eventflow.data.dir", tempDir.toString());
        PersistenceService ps = new PersistenceService();
        ps.objectMapper = new ObjectMapper();
        ps.init();
        UserScheduleService svc = new UserScheduleService();
        Field f = UserScheduleService.class.getDeclaredField("persistence");
        f.setAccessible(true);
        f.set(svc, ps);
        svc.init();
        return svc;
    }

    private PersistenceService getPersistence(UserScheduleService svc) throws Exception {
        Field f = UserScheduleService.class.getDeclaredField("persistence");
        f.setAccessible(true);
        return (PersistenceService) f.get(svc);
    }

    private void flush(UserScheduleService svc) throws Exception {
        getPersistence(svc).flush();
    }

    @Test
    public void updateDetailsAndSummary() throws Exception {
        UserScheduleService svc = newService();
        try {
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
        } finally {
            flush(svc);
        }
    }

    @Test
    public void removeTalk() throws Exception {
        UserScheduleService svc = newService();
        try {
            String user = "user@example.com";

            assertTrue(svc.addTalkForUser(user, "t1"));
            assertTrue(svc.removeTalkForUser(user, "t1"));
            assertFalse(svc.removeTalkForUser(user, "t1"));

            UserScheduleService.Summary summary = svc.getSummaryForUser(user);
            assertEquals(0, summary.total());
            assertEquals(0, summary.attended());
            assertEquals(0, summary.rated());
        } finally {
            flush(svc);
        }
    }

    @Test
    public void ignoreNullUser() throws Exception {
        UserScheduleService svc = newService();
        try {
            assertFalse(svc.addTalkForUser(null, "t1"));
            assertTrue(svc.getTalksForUser(null).isEmpty());
            assertTrue(svc.getTalkDetailsForUser(null).isEmpty());
            assertEquals(0, svc.getSummaryForUser(null).total());
        } finally {
            flush(svc);
        }
    }

    @Test
    public void persistsAndLoadsLastYear() throws Exception {
        UserScheduleService svc = newService();
        try {
            String user = "user@example.com";
            assertTrue(svc.addTalkForUser(user, "t1"));
            flush(svc);

            // recreate service to simulate restart
            UserScheduleService svc2 = newService();
            try {
                assertTrue(svc2.getTalksForUser(user).contains("t1"));
            } finally {
                flush(svc2);
            }
        } finally {
            flush(svc);
        }
    }

    @Test
    public void ignoresOlderFiles() throws Exception {
        int year = Year.now().getValue() - 2;
        Path oldFile = tempDir.resolve("user-schedule-" + year + ".json");
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(oldFile.toFile(), Map.of("u", Map.of("t1", new UserScheduleService.TalkDetails())));

        UserScheduleService svc = newService();
        try {
            assertTrue(svc.getTalksForUser("u").isEmpty());
        } finally {
            flush(svc);
        }
    }
}


