package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanales.eventflow.cfp.CfpSubmission;
import com.scanales.eventflow.cfp.CfpSubmissionStatus;
import com.scanales.eventflow.model.Event;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PersistenceServiceTest {

  @TempDir Path tempDir;

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

  @Test
  void flushDrainsDebouncedPendingWrites() {
    service = newService();
    service.writeCoalesceWindowMs = 5_000L;

    service.saveEvents(Map.of("event-1", event("event-1", "Debounced title")));
    service.flush();

    Map<String, Event> persisted = service.loadEvents();
    assertEquals("Debounced title", persisted.get("event-1").getTitle());
  }

  @Test
  void rapidWritesAreCoalescedPerFile() {
    service = newService();
    service.writeCoalesceWindowMs = 400L;

    for (int i = 0; i < 10; i++) {
      service.saveEvents(Map.of("event-1", event("event-1", "Title " + i)));
    }
    service.flush();

    Map<String, Event> persisted = service.loadEvents();
    assertEquals("Title 9", persisted.get("event-1").getTitle());
    assertTrue(service.getQueueStats().writesCoalesced() >= 9);
    assertEquals(1, service.getQueueStats().writesOk());
  }

  @Test
  void cfpSyncSaveCreatesBackupSnapshot() {
    service = newService();

    service.saveCfpSubmissionsSync(Map.of("cfp-1", cfp("cfp-1", "Reliable CFP pipelines")));

    Path backupsDir = tempDir.resolve("backups").resolve("cfp");
    assertTrue(Files.exists(backupsDir));
    try (var stream = Files.list(backupsDir)) {
      long count = stream.filter(Files::isRegularFile).count();
      assertTrue(count >= 1);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void cfpLoadRecoversFromBackupWhenPrimaryIsCorrupted() throws Exception {
    service = newService();

    CfpSubmission first = cfp("cfp-1", "Resilient CFP storage");
    service.saveCfpSubmissionsSync(Map.of(first.id(), first));

    Path primary = tempDir.resolve("cfp-submissions.json");
    assertTrue(Files.exists(primary));
    Files.writeString(primary, "{corrupted-json", java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

    Map<String, CfpSubmission> recovered = service.loadCfpSubmissions();
    assertEquals(1, recovered.size());
    assertNotNull(recovered.get(first.id()));

    try (var stream = Files.list(tempDir)) {
      boolean hasCorruptCopy =
          stream.anyMatch(
              p ->
                  p.getFileName() != null
                      && p.getFileName().toString().startsWith("cfp-submissions.corrupt-"));
      assertTrue(hasCorruptCopy);
    }

    String current = Files.readString(primary);
    assertFalse(current.contains("corrupted-json"));
  }

  @Test
  void cfpSyncSaveWritesWalFrame() throws Exception {
    service = newService();
    service.cfpBackupsEnabled = false;

    service.saveCfpSubmissionsSync(Map.of("cfp-1", cfp("cfp-1", "WAL baseline")));

    Path wal = tempDir.resolve("cfp-submissions.wal");
    assertTrue(Files.exists(wal));
    assertTrue(Files.size(wal) > 0);
    assertEquals(1, walFrameCount(wal));
  }

  @Test
  void cfpLoadRecoversFromWalWhenPrimaryIsMissing() throws Exception {
    service = newService();
    service.cfpBackupsEnabled = false;

    CfpSubmission first = cfp("cfp-1", "Recover from WAL");
    service.saveCfpSubmissionsSync(Map.of(first.id(), first));

    Path primary = tempDir.resolve("cfp-submissions.json");
    assertTrue(Files.exists(primary));
    Files.delete(primary);

    Map<String, CfpSubmission> recovered = service.loadCfpSubmissions();
    assertEquals(1, recovered.size());
    assertNotNull(recovered.get(first.id()));
    assertTrue(Files.exists(primary));
  }

  @Test
  void cfpWalCompactsToSingleFrameWhenExceedingMaxBytes() throws Exception {
    service = newService();
    service.cfpBackupsEnabled = false;
    service.cfpWalMaxBytes = 128L;

    for (int i = 0; i < 5; i++) {
      service.saveCfpSubmissionsSync(Map.of("cfp-1", cfp("cfp-1", "Compaction " + i)));
    }

    Path wal = tempDir.resolve("cfp-submissions.wal");
    assertTrue(Files.exists(wal));
    assertEquals(1, walFrameCount(wal));
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

  private CfpSubmission cfp(String id, String title) {
    Instant now = Instant.parse("2026-02-12T00:00:00Z");
    return new CfpSubmission(
        id,
        "event-1",
        "member@example.com",
        "Member",
        title,
        "Summary",
        "Abstract",
        "intermediate",
        "talk",
        30,
        "en",
        "platform-engineering-idp",
        java.util.List.of("platform"),
        java.util.List.of("https://example.org/talk"),
        CfpSubmissionStatus.PENDING,
        now,
        now,
        null,
        null,
        null,
        null,
        null,
        null);
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

  private int walFrameCount(Path walPath) throws Exception {
    byte[] data = Files.readAllBytes(walPath);
    if (data.length < Integer.BYTES) {
      return 0;
    }
    ByteBuffer buffer = ByteBuffer.wrap(data);
    int frames = 0;
    while (buffer.remaining() >= Integer.BYTES) {
      int length = buffer.getInt();
      if (length <= 0 || length > buffer.remaining()) {
        break;
      }
      buffer.position(buffer.position() + length);
      frames += 1;
    }
    return frames;
  }
}
