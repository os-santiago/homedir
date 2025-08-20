package com.scanales.eventflow;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class TestDataDir implements QuarkusTestResourceLifecycleManager {
  private Path tempDir;

  @Override
  public Map<String, String> start() {
    try {
      tempDir = Files.createTempDirectory("eventflow-test");
      String path = tempDir.toString();
      System.setProperty("eventflow.data.dir", path);
      return Map.of("eventflow.data.dir", path);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stop() {
    // no cleanup required
  }
}
