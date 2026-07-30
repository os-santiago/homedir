package com.scanales.homedir.service;

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
  void restoreArchiveRejectsNullByteInEntryName() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
      zos.putNextEntry(new ZipEntry("evil\0.txt"));
      zos.write("bad".getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }

    byte[] zip = baos.toByteArray();
    Path restoreDir = tempDir.resolve("restore-nullbyte");
    Files.createDirectories(restoreDir);

    assertThrows(
        Exception.class, () -> service.restoreArchive(new ByteArrayInputStream(zip), restoreDir));
  }

  @Test
  void restoreArchiveRejectsSymlinkAtTarget() throws Exception {
    Path restoreDir = tempDir.resolve("restore-symlink");
    Files.createDirectories(restoreDir);
    // Create a symlink inside the restore dir pointing outside
    Path outsideFile = tempDir.resolve("outside-secret.txt");
    Files.writeString(outsideFile, "secret", StandardCharsets.UTF_8);
    Path link = restoreDir.resolve("stolen-link.txt");
    try {
      Files.createSymbolicLink(link, outsideFile);
    } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
      // Symlinks not supported on this filesystem - skip test
      return;
    }

    // Create a ZIP that tries to write to the symlink path
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
      zos.putNextEntry(new ZipEntry("stolen-link.txt"));
      zos.write("overwritten".getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }

    assertThrows(
        Exception.class,
        () -> service.restoreArchive(new ByteArrayInputStream(baos.toByteArray()), restoreDir));
  }
}
