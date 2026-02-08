package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.eventflow.model.Event;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PersistenceServiceTest {

  @TempDir
  Path tempDir;

  private PersistenceService service;

  @AfterEach
  void cleanup() {
    if (service != null) {
      service.shutdown();
    }
    System.clearProperty("homedir.data.dir");
  }

  @Test
  void flushWaitsForScheduledRetryAndPersistsFile() throws Exception {
    service = newService();

    Path blockedEventsPath = tempDir.resolve("events.json");
    Files.createDirectory(blockedEventsPath);

    service.saveEvents(Map.of("event-1", event("event-1", "First title")));
    assertTrue(waitUntil(() -> service.getQueueStats().writesRetries() > 0, Duration.ofSeconds(5)));

    Files.delete(blockedEventsPath);
    service.flush();

    Map<String, Event> persisted = service.loadEvents();
    assertEquals(1, persisted.size());
    assertEquals("First title", persisted.get("event-1").getTitle());
    assertNull(service.getQueueStats().lastError());
  }

  @Test
  void latestVersionWinsWhenMultipleWritesAreQueued() {
    service = newService();

    service.saveEvents(Map.of("event-1", event("event-1", "Old title")));
    service.saveEvents(Map.of("event-1", event("event-1", "New title")));
    service.flush();

    Map<String, Event> persisted = service.loadEvents();
    assertNotNull(persisted.get("event-1"));
    assertEquals("New title", persisted.get("event-1").getTitle());
  }

  private PersistenceService newService() {
    System.setProperty("homedir.data.dir", tempDir.toString());
    PersistenceService ps = new PersistenceService();
    ps.objectMapper = new ObjectMapper();
    ps.init();
    return ps;
  }

  private Event event(String id, String title) {
    return new Event(id, title, "description");
  }

  private boolean waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return true;
      }
      Thread.sleep(25);
    }
    return false;
  }
}
