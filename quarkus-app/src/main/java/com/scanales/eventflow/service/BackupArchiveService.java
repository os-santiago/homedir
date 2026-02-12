package com.scanales.eventflow.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
public class BackupArchiveService {

  public byte[] createArchive(Path dataDir, String appVersion) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
      List<Path> files = new ArrayList<>();
      if (Files.exists(dataDir)) {
        try (var stream = Files.walk(dataDir)) {
          stream
              .filter(Files::isRegularFile)
              .sorted(Comparator.comparing(Path::toString))
              .forEach(files::add);
        }
      }

      for (Path file : files) {
        String entryName = toEntryName(dataDir.relativize(file));
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        try (InputStream in = Files.newInputStream(file)) {
          in.transferTo(zos);
        }
        zos.closeEntry();
      }

      String manifest =
          "{\n"
              + "  \"generated_at\": \""
              + Instant.now()
              + "\",\n"
              + "  \"app_version\": \""
              + escapeJson(appVersion)
              + "\",\n"
              + "  \"files\": "
              + files.size()
              + "\n"
              + "}\n";
      ZipEntry manifestEntry = new ZipEntry("backup-manifest.json");
      zos.putNextEntry(manifestEntry);
      zos.write(manifest.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    return baos.toByteArray();
  }

  public int restoreArchive(InputStream zipInput, Path dataDir) throws IOException {
    Files.createDirectories(dataDir);
    int restoredFiles = 0;
    try (ZipInputStream zis = new ZipInputStream(zipInput, StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        String rawName = entry.getName() == null ? "" : entry.getName().trim();
        if (rawName.isBlank()) {
          zis.closeEntry();
          continue;
        }
        if ("backup-manifest.json".equals(rawName)) {
          zis.closeEntry();
          continue;
        }

        Path target = safeResolve(dataDir, rawName);
        if (entry.isDirectory()) {
          Files.createDirectories(target);
          zis.closeEntry();
          continue;
        }

        Path parent = target.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
        restoredFiles++;
        zis.closeEntry();
      }
    }
    return restoredFiles;
  }

  static Path safeResolve(Path dataDir, String entryName) throws IOException {
    String normalized = entryName.replace('\\', '/');
    boolean hadLeadingSlash = normalized.startsWith("/");
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    if (hadLeadingSlash) {
      throw new IOException("zip_absolute_path_detected");
    }
    if (normalized.isBlank()) {
      throw new IOException("invalid_entry_name");
    }
    if (normalized.contains("../") || normalized.contains("..\\") || normalized.startsWith("..")) {
      throw new IOException("zip_path_traversal_detected");
    }
    if (normalized.matches("^[A-Za-z]:.*")) {
      throw new IOException("zip_absolute_path_detected");
    }

    Path target = dataDir.resolve(normalized).normalize();
    Path root = dataDir.normalize();
    if (!target.startsWith(root)) {
      throw new IOException("zip_outside_data_dir");
    }
    return target;
  }

  private static String toEntryName(Path relativePath) {
    String name = relativePath.toString().replace('\\', '/');
    while (name.startsWith("/")) {
      name = name.substring(1);
    }
    return name;
  }

  private static String escapeJson(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}

