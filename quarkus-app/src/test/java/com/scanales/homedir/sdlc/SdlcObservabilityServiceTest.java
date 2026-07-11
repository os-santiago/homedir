package com.scanales.homedir.sdlc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SdlcObservabilityServiceTest {
  @TempDir Path temp;
  private SdlcObservabilityService service;

  @BeforeEach
  void setUp() {
    service = new SdlcObservabilityService(new ObjectMapper());
    service.configuredStateDir = temp.toString();
    service.workerVersion = "test-1";
  }

  @Test
  void parsesHeartbeatAndMapsHealthyWorkerState() throws Exception {
    Files.writeString(
        temp.resolve("heartbeat.json"),
        "{\"status\":\"running\",\"detail\":\"working\",\"updated_at\":\"" + Instant.now() + "\"}");
    Map<?, ?> worker = (Map<?, ?>) service.status().get("worker");
    assertEquals("running", worker.get("state"));
    assertFalse((Boolean) service.heartbeat().get("stale"));
  }

  @Test
  void groupsIssueSnapshotsIntoPipelineStages() throws Exception {
    Path issues = Files.createDirectory(temp.resolve("issues"));
    Files.writeString(
        issues.resolve("42.json"),
        "{\"number\":42,\"title\":\"Atomic task\",\"state\":\"queued\",\"updated_at\":\""
            + Instant.now()
            + "\"}");
    List<Map<String, Object>> stages = service.pipeline();
    Map<String, Object> queued =
        stages.stream().filter(s -> "queued".equals(s.get("id"))).findFirst().orElseThrow();
    assertEquals(1, queued.get("count"));
    assertEquals(42L, ((List<Map<String, Object>>) queued.get("items")).getFirst().get("number"));
  }

  @Test
  void detectsStaleAdmissionAndWritesAuditedControls() throws Exception {
    Path issues = Files.createDirectory(temp.resolve("issues"));
    Files.writeString(
        issues.resolve("9.json"),
        "{\"number\":9,\"state\":\"admission\",\"updated_at\":\"2020-01-01T00:00:00Z\"}");
    assertTrue(service.anomalies().stream().anyMatch(a -> "issue-9".equals(a.get("id"))));
    service.control("pause", "operator@example.test");
    assertTrue(Files.exists(temp.resolve("paused")));
    assertTrue(
        Files.readString(temp.resolve("admin-audit.jsonl")).contains("operator@example.test"));
    service.control("resume", "operator@example.test");
    assertFalse(Files.exists(temp.resolve("paused")));
    assertThrows(IllegalArgumentException.class, () -> service.control("restart", "operator"));
  }

  @Test
  void rejectsUnsafeAuditIdentifiers() {
    assertTrue(service.audit("../../etc/passwd").isEmpty());
  }
}
