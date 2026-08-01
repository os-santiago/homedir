package com.scanales.homedir.achievements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.achievements.Achievement.Status;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for the static Phase 1 achievement catalog (issue #1323, parent #1043). */
class AchievementCatalogTest {

  private final AchievementCatalog catalog = new AchievementCatalog();

  @Test
  void catalogContainsExpectedAchievements() {
    List<AchievementCatalog.AchievementGuide> guides = catalog.guides();
    assertEquals(9, guides.size(), "Phase 1 catalog should list 9 achievements");

    Set<String> keys = new HashSet<>();
    for (AchievementCatalog.AchievementGuide guide : guides) {
      keys.add(guide.achievement().key());
    }
    assertTrue(keys.contains("pull-shark"));
    assertTrue(keys.contains("quickdraw"));
    assertTrue(keys.contains("pair-extraordinaire"));
    assertTrue(keys.contains("galaxy-brain"));
  }

  @Test
  void allPhase1AchievementsAreLocked() {
    for (AchievementCatalog.AchievementGuide guide : catalog.guides()) {
      assertEquals(
          Status.LOCKED,
          guide.achievement().status(),
          "Phase 1 has no GitHub API verification; every achievement must be LOCKED");
    }
  }

  @Test
  void everyAchievementHasBilingualDescriptionsAndDocUrl() {
    for (AchievementCatalog.AchievementGuide guide : catalog.guides()) {
      Achievement a = guide.achievement();
      assertNotNull(a.title());
      assertFalse(a.description().isBlank(), "EN description required for " + a.key());
      assertFalse(a.descriptionEs().isBlank(), "ES description required for " + a.key());
      assertTrue(a.docUrl().startsWith("https://"), "docUrl must be https for " + a.key());
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
}
