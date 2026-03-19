package com.scanales.homedir.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommunityContentServiceTest {

  @Test
  void syncBundledSeedContentCopiesValidItemsAndPrunesOldManagedFiles() throws Exception {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    List<String> indexedFiles = CommunityContentService.readBundledSeedIndex(classLoader);
    assertFalse(indexedFiles.isEmpty());

    Path contentDir = Files.createTempDirectory("community-seed-sync");
    CommunityContentService.SeedSyncResult firstSync =
        CommunityContentService.syncBundledSeedContent(contentDir, classLoader);

    assertEquals(indexedFiles.size(), firstSync.totalFiles());
    assertEquals(indexedFiles.size(), firstSync.writtenFiles());
    assertEquals(0, firstSync.removedFiles());

    CommunityContentParser parser = new CommunityContentParser();
    for (String fileName : indexedFiles) {
      Path seededFile = contentDir.resolve(fileName);
      assertTrue(Files.exists(seededFile));
      var parsed = parser.parse(seededFile);
      assertTrue(parsed.isValid(), fileName);
    }

    Path legacyManagedFile = contentDir.resolve("legacy-managed.yml");
    Files.writeString(
        legacyManagedFile,
        """
        id: "legacy-managed"
        title: "Legacy managed file"
        url: "https://example.org/legacy-managed"
        summary: "Legacy managed file to prune."
        source: "example.org"
        created_at: "2026-03-01T00:00:00Z"
        """);
    Files.writeString(
        contentDir.resolve(CommunityContentService.MANAGED_SEED_MANIFEST),
        String.join(System.lineSeparator(), indexedFiles) + System.lineSeparator() + "legacy-managed.yml");

    CommunityContentService.SeedSyncResult secondSync =
        CommunityContentService.syncBundledSeedContent(contentDir, classLoader);

    assertEquals(indexedFiles.size(), secondSync.totalFiles());
    assertEquals(0, secondSync.writtenFiles());
    assertEquals(1, secondSync.removedFiles());
    assertFalse(Files.exists(legacyManagedFile));
  }
}
