package com.scanales.homedir.achievements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.achievements.Achievement.VerificationType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for the achievement catalog and progress tracking (issue #1043). */
class AchievementCatalogTest {

  private final AchievementCatalog catalog = new AchievementCatalog();

  @Test
  void catalogContainsExpectedAchievements() {
    List<AchievementCatalog.AchievementGuide> guides = catalog.guides();
    assertEquals(9, guides.size(), "Catalog should list 9 achievements matching issue #1043");

    Set<String> keys = new HashSet<>();
    for (AchievementCatalog.AchievementGuide guide : guides) {
      keys.add(guide.achievement().key());
    }
    assertTrue(keys.contains("pull-shark"));
    assertTrue(keys.contains("quickdraw"));
    assertTrue(keys.contains("pair-extraordinaire"));
    assertTrue(keys.contains("galaxy-brain"));
    assertTrue(keys.contains("yolo"));
    assertTrue(keys.contains("starstruck"));
  }

  @Test
  void everyAchievementHasBilingualDescriptionsAndDocUrl() {
    for (AchievementCatalog.AchievementGuide guide : catalog.guides()) {
      Achievement a = guide.achievement();
      assertNotNull(a.title());
      assertFalse(a.description().isBlank(), "EN description required for " + a.key());
      assertFalse(a.descriptionEs().isBlank(), "ES description required for " + a.key());
      assertTrue(a.docUrl().startsWith("https://"), "docUrl must be https for " + a.key());
      assertTrue(a.threshold() > 0, "threshold must be positive for " + a.key());
      assertNotNull(a.verification(), "verification type required for " + a.key());
    }
  }

  @Test
  void everyAchievementMapsToAtLeastOneOrgRepo() {
    for (AchievementCatalog.AchievementGuide guide : catalog.guides()) {
      assertFalse(guide.repos().isEmpty(), "every achievement must map to >=1 org repo");
      for (AchievementCatalog.OrgRepo repo : guide.repos()) {
        assertTrue(catalog.orgRepos().contains(repo), "repo must be part of the org repo list");
      }
    }
  }

  @Test
  void orgRepoListMatchesIssue() {
    Set<String> names = new HashSet<>();
    for (AchievementCatalog.OrgRepo repo : catalog.orgRepos()) {
      names.add(repo.name());
      assertTrue(repo.url().startsWith("https://github.com/os-santiago/"));
    }
    assertTrue(names.contains("os-santiago/homedir"));
    assertTrue(names.contains("os-santiago/demo-repository"));
    assertTrue(names.contains("os-santiago/open-quest"));
  }

  @Test
  void verifiableAchievementsUseApiVerification() {
    Achievement pullShark = catalog.find("pull-shark");
    assertEquals(VerificationType.MERGED_PRS, pullShark.verification());
    assertEquals(1, pullShark.threshold());

    Achievement pair = catalog.find("pair-extraordinaire");
    assertEquals(VerificationType.COAUTHORED_PRS, pair.verification());

    Achievement yolo = catalog.find("yolo");
    assertEquals(VerificationType.MERGED_PRS_NO_REVIEW, yolo.verification());

    Achievement starstruck = catalog.find("starstruck");
    assertEquals(VerificationType.REPO_STARS, starstruck.verification());
    assertEquals(16, starstruck.threshold());
  }

  @Test
  void manualOnlyAchievementsAreMarked() {
    Achievement quickdraw = catalog.find("quickdraw");
    assertEquals(VerificationType.MANUAL_ONLY, quickdraw.verification());

    Achievement galaxyBrain = catalog.find("galaxy-brain");
    assertEquals(VerificationType.MANUAL_ONLY, galaxyBrain.verification());
  }

  @Test
  void progressLockedForZeroCount() {
    Achievement a = catalog.find("pull-shark");
    AchievementProgress p = AchievementProgress.fromCount(a, 0);
    assertEquals(AchievementProgress.Status.LOCKED, p.status());
    assertEquals(0, p.percent());
  }

  @Test
  void progressInProgressForPartialCount() {
    Achievement a = catalog.find("starstruck");
    AchievementProgress p = AchievementProgress.fromCount(a, 8);
    assertEquals(AchievementProgress.Status.IN_PROGRESS, p.status());
    assertEquals(50, p.percent());
  }

  @Test
  void progressCompletedAtThreshold() {
    Achievement a = catalog.find("pull-shark");
    AchievementProgress p = AchievementProgress.fromCount(a, 1);
    assertEquals(AchievementProgress.Status.COMPLETED, p.status());
    assertEquals(100, p.percent());
  }

  @Test
  void progressCompletedAboveThreshold() {
    Achievement a = catalog.find("starstruck");
    AchievementProgress p = AchievementProgress.fromCount(a, 20);
    assertEquals(AchievementProgress.Status.COMPLETED, p.status());
    assertEquals(100, p.percent(), "percent should cap at 100");
  }
}
