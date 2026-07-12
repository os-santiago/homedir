package com.scanales.homedir.sdlc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SdlcDashboardSnapshotTest {
  @TempDir Path temp;
  private SdlcDashboardSnapshot snapshot;
  private Path journal;

  @BeforeEach
  void setUp() throws Exception {
    journal = Files.createDirectories(temp.resolve("run-summaries"));
    snapshot = new SdlcDashboardSnapshot(new ObjectMapper());
    snapshot.stateDir = temp.toString();
    snapshot.workerVersion = "test";
    snapshot.controlsEnabled = false;
    snapshot.maxEvents = 100;
    snapshot.maxFilesPerCycle = 10;
    snapshot.maxBytesPerCycle = 4096;
  }

  @Test
  void incrementallyConsumesAppendOnlyEventsWithoutDuplicates() throws Exception {
    Path file = journal.resolve("issue-42.jsonl");
    Files.writeString(file, event(42, "queued", null));
    snapshot.refresh();

    assertEquals(1, ((List<?>) snapshot.get().get("issues")).size());
    assertEquals(1, snapshot.audit("42").size());

    snapshot.refresh();
    assertEquals(1, snapshot.audit("42").size(), "unchanged bytes must not be re-read");

    Files.writeString(file, event(42, "pr-opened", 99), java.nio.file.StandardOpenOption.APPEND);
    snapshot.refresh();
    assertEquals(2, snapshot.audit("42").size());
    assertEquals(1, ((List<?>) snapshot.get().get("prs")).size());
    assertFalse((Boolean) snapshot.get().get("stale"));
  }

  @Test
  void isolatesMalformedLinesAndEnforcesBoundedMemory() throws Exception {
    Path file = journal.resolve("issue-7.jsonl");
    StringBuilder data = new StringBuilder("not-json\n");
    for (int i = 0; i < 140; i++) data.append(event(7, "queued", null));
    Files.writeString(file, data);
    snapshot.maxBytesPerCycle = 65536;
    snapshot.refresh();

    assertEquals(100, snapshot.audit("7").size());
  }

  private String event(int issue, String event, Integer pr) {
    return "{\"issue\":" + issue + ",\"event\":\"" + event + "\",\"pr_number\":"
        + (pr == null ? "null" : pr) + ",\"created_at\":\"2026-07-12T15:00:00Z\"}\n";
  }
}
