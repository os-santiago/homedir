package com.scanales.homedir.achievements;

import com.scanales.homedir.achievements.AchievementCatalog.Achievement;
import com.scanales.homedir.achievements.AchievementCatalog.AchievementGuide;
import com.scanales.homedir.achievements.AchievementCatalog.OrgRepo;
import java.util.List;

/**
 * View model for a single achievement card in the Achievement Hub template.
 *
 * <p>Pre-computes the display data (localized description, steps, threshold) so the Qute template
 * stays simple. Phase 1 is read-only: all achievements display as "locked" since there is no
 * per-user progress tracking or API verification yet.
 */
public record AchievementView(
    String key,
    String title,
    String description,
    String category,
    String docUrl,
    int threshold,
    List<String> steps,
    List<OrgRepo> repos) {

  /** Builds a read-only view model from a guide. Locale selects EN or ES descriptions/steps. */
  public static AchievementView from(AchievementGuide guide, String locale) {
    Achievement a = guide.achievement();
    boolean isEn = "en".equals(locale);
    String desc = isEn ? a.description() : a.descriptionEs();
    List<String> steps = isEn ? a.steps() : a.stepsEs();
    return new AchievementView(
        a.key(),
        a.title(),
        desc,
        a.category(),
        a.docUrl(),
        a.threshold(),
        steps,
        guide.repos());
  }
}
