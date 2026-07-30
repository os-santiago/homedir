package com.scanales.homedir.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BackupArchiveServiceTest {

  @TempDir Path tempDir;

  private final BackupArchiveService service = new BackupArchiveService();

  @BeforeEach
  void setUp() {
    service.objectMapper = new ObjectMapper();
  }

  @Test
  void createArchiveIncludesNestedFilesAndManifest() throws Exception {
    Path dataDir = tempDir.resolve("data");
    Files.createDirectories(dataDir.resolve("nested").resolve("deep"));
    Files.writeString(dataDir.resolve("events.json"), "{}", StandardCharsets.UTF_8);
    Files.writeString(
        dataDir.resolve("nested").resolve("deep").resolve("cfp-submissions.json"),
        "{}",
        StandardCharsets.UTF_8);

    byte[] zip = service.createArchive(dataDir, "3.338.0");
    assertTrue(zip.length > 0);

    Path restoreDir = tempDir.resolve("restore");
    int restored = service.restoreArchive(new ByteArrayInputStream(zip), restoreDir);

    assertEquals(2, restored);
    assertTrue(Files.exists(restoreDir.resolve("events.json")));
    assertTrue(
        Files.exists(restoreDir.resolve("nested").resolve("deep").resolve("cfp-submissions.json")));
  }

  @Test
  void safeResolveRejectsPathTraversal() {
    assertThrows(
        Exception.class, () -> BackupArchiveService.safeResolve(tempDir, "../secrets.txt"));
    assertThrows(
        Exception.class, () -> BackupArchiveService.safeResolve(tempDir, "..\\secrets.txt"));
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

  @Test
  void readManifestVersionExtractsVersionFromArchive() throws Exception {
    Path dataDir = tempDir.resolve("data");
    Files.createDirectories(dataDir);
    Files.writeString(dataDir.resolve("events.json"), "{}", StandardCharsets.UTF_8);

    byte[] zip = service.createArchive(dataDir, "3.403.1");
    Optional<String> version = service.readManifestVersion(new ByteArrayInputStream(zip));

    assertTrue(version.isPresent());
    assertEquals("3.403.1", version.get());
  }

  @Test
  void readManifestVersionReturnsEmptyWhenNoManifest() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
      zos.putNextEntry(new ZipEntry("events.json"));
      zos.write("{}".getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }

    Optional<String> version =
        service.readManifestVersion(new ByteArrayInputStream(baos.toByteArray()));
    assertTrue(version.isEmpty());
  }

  @Test
  void restoreWorksRegardlessOfFilenameWhenManifestPresent() throws Exception {
    Path dataDir = tempDir.resolve("data");
    Files.createDirectories(dataDir);
    Files.writeString(dataDir.resolve("events.json"), "{\"key\":\"value\"}", StandardCharsets.UTF_8);

    byte[] zip = service.createArchive(dataDir, "3.403.1");

    // The restore should work even though we are not relying on a filename pattern.
    Path restoreDir = tempDir.resolve("restore");
    int restored = service.restoreArchive(new ByteArrayInputStream(zip), restoreDir);
    assertEquals(1, restored);
    assertTrue(Files.exists(restoreDir.resolve("events.json")));

    // The manifest version should be readable regardless of the original filename.
    Optional<String> version = service.readManifestVersion(new ByteArrayInputStream(zip));
    assertTrue(version.isPresent());
    assertEquals("3.403.1", version.get());
  }
}
