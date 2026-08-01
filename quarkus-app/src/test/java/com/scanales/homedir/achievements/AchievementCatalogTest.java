package com.scanales.homedir.achievements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for the achievement catalog (issue #1043, Phase 1). */
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
    assertTrue(keys.contains("yolo"));
    assertTrue(keys.contains("starstruck"));
    assertTrue(keys.contains("galaxy-brain"));
    assertTrue(keys.contains("public-sponsor"));
    assertTrue(keys.contains("heart-on-your-sleeve"));
    assertTrue(keys.contains("open-sourcerer"));
  }

  @Test
  void everyAchievementHasBilingualDescriptionsAndDocUrl() {
    for (AchievementCatalog.AchievementGuide guide : catalog.guides()) {
      AchievementCatalog.Achievement a = guide.achievement();
      assertNotNull(a.title());
      assertFalse(a.description().isBlank(), "EN description required for " + a.key());
      assertFalse(a.descriptionEs().isBlank(), "ES description required for " + a.key());
      assertTrue(a.docUrl().startsWith("https://"), "docUrl must be https for " + a.key());
      assertTrue(a.threshold() > 0, "threshold must be positive for " + a.key());
      // Interactive guides (issue #1043 criterion #2)
      assertNotNull(a.steps(), "steps must not be null for " + a.key());
      assertNotNull(a.stepsEs(), "stepsEs must not be null for " + a.key());
      assertFalse(a.steps().isEmpty(), "steps must not be empty for " + a.key());
      assertFalse(a.stepsEs().isEmpty(), "stepsEs must not be empty for " + a.key());
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
  }

  @Test
  void findReturnsAchievementByKey() {
    AchievementCatalog.Achievement pullShark = catalog.find("pull-shark");
    assertNotNull(pullShark);
    assertEquals("Pull Shark", pullShark.title());
    assertEquals(1, pullShark.threshold());
  }

  @Test
  void findReturnsNullForUnknownKey() {
    AchievementCatalog.Achievement unknown = catalog.find("nonexistent");
    assertNull(unknown);
  }
}
