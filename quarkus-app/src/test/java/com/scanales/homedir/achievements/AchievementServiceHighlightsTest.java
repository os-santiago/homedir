package com.scanales.homedir.achievements;

import static org.junit.jupiter.api.Assertions.*;

import com.scanales.homedir.achievements.AchievementService.Highlight;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AchievementServiceHighlightsTest {

  @Inject AchievementService achievementService;

  @Test
  void highlightsReturnsFourEntriesWithIconUrls() {
    List<Highlight> highlights = achievementService.highlights();

    assertEquals(4, highlights.size(), "should return exactly 4 highlights");

    for (Highlight h : highlights) {
      assertNotNull(h.key(), "highlight key must not be null");
      assertFalse(h.key().isBlank(), "highlight key must not be blank");
      assertNotNull(h.iconUrl(), "highlight iconUrl must not be null");
      assertFalse(h.iconUrl().isBlank(), "highlight iconUrl must not be blank");
      assertNotNull(h.profileLabel(), "highlight profileLabel must not be null");
      assertFalse(h.profileLabel().isBlank(), "highlight profileLabel must not be blank");
    }
  }

  @Test
  void highlightsContainExpectedKeys() {
    List<Highlight> highlights = achievementService.highlights();

    List<String> keys = highlights.stream().map(Highlight::key).toList();
    assertTrue(keys.contains("pro"), "should contain 'pro' highlight");
    assertTrue(keys.contains("developer-program"), "should contain 'developer-program' highlight");
    assertTrue(keys.contains("security-bounty"), "should contain 'security-bounty' highlight");
    assertTrue(
        keys.contains("galaxy-brain-highlight"),
        "should contain 'galaxy-brain-highlight' highlight");
  }

  @Test
  void profileLabelsAreLocalizedAndNotEmpty() {
    List<Highlight> highlights = achievementService.highlights();

    for (Highlight h : highlights) {
      String label = h.profileLabel();
      assertFalse(
          label.isEmpty(),
          "profileLabel for '" + h.key() + "' should be a resolved i18n string, not empty");
    }
  }
}
