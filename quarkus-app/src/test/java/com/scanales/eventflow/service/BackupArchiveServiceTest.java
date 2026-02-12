package com.scanales.eventflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BackupArchiveServiceTest {

  @TempDir Path tempDir;

  private final BackupArchiveService service = new BackupArchiveService();

  @Test
  void createArchiveIncludesNestedFilesAndManifest() throws Exception {
    Path dataDir = tempDir.resolve("data");
    Files.createDirectories(dataDir.resolve("nested").resolve("deep"));
    Files.writeString(dataDir.resolve("events.json"), "{}", StandardCharsets.UTF_8);
    Files.writeString(dataDir.resolve("nested").resolve("deep").resolve("cfp-submissions.json"), "{}", StandardCharsets.UTF_8);

    byte[] zip = service.createArchive(dataDir, "3.338.0");
    assertTrue(zip.length > 0);

    Path restoreDir = tempDir.resolve("restore");
    int restored = service.restoreArchive(new ByteArrayInputStream(zip), restoreDir);

    assertEquals(2, restored);
    assertTrue(Files.exists(restoreDir.resolve("events.json")));
    assertTrue(Files.exists(restoreDir.resolve("nested").resolve("deep").resolve("cfp-submissions.json")));
  }

  @Test
  void safeResolveRejectsPathTraversal() {
    assertThrows(Exception.class, () -> BackupArchiveService.safeResolve(tempDir, "../secrets.txt"));
    assertThrows(Exception.class, () -> BackupArchiveService.safeResolve(tempDir, "..\\secrets.txt"));
    assertThrows(Exception.class, () -> BackupArchiveService.safeResolve(tempDir, "/etc/passwd"));
  }

  @Test
  void restoreArchiveRejectsZipSlipEntries() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
      zos.putNextEntry(new ZipEntry("../escape.txt"));
      zos.write("bad".getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }

    byte[] zip = baos.toByteArray();
    Path restoreDir = tempDir.resolve("restore");
    Files.createDirectories(restoreDir);

    Exception error =
        assertThrows(
            Exception.class,
            () -> service.restoreArchive(new ByteArrayInputStream(zip), restoreDir));
    assertNotNull(error.getMessage());
  }
}

