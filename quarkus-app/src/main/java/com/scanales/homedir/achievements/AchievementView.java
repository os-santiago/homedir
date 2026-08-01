package com.scanales.homedir.achievements;

import com.scanales.homedir.achievements.AchievementCatalog.Achievement;
import com.scanales.homedir.achievements.AchievementCatalog.AchievementGuide;
import com.scanales.homedir.achievements.AchievementCatalog.OrgRepo;
import com.scanales.homedir.achievements.AchievementProgress.AchievementState;
import java.util.List;

/**
 * View model for a single achievement card in the Achievement Hub template.
 *
 * <p>Pre-computes the display data (status, progress, localized description and steps) so the Qute
 * template stays simple.
 */
public record AchievementView(
    String key,
    String title,
    String description,
    String category,
    String docUrl,
    String status,
    int progressCount,
    int progressTarget,
    String verifiedVia,
    List<String> steps,
    List<OrgRepo> repos) {

  /** Builds a view model from a guide and the user's progress state (may be null). */
  public static AchievementView from(
      AchievementGuide guide, AchievementState state, String locale) {
    Achievement a = guide.achievement();
    String status = state != null ? state.status() : "locked";
    int count = state != null ? state.progressCount() : 0;
    int target = a.threshold();
    String via = state != null ? state.verifiedVia() : null;
    boolean isEn = "en".equals(locale);
    String desc = isEn ? a.description() : a.descriptionEs();
    List<String> steps = isEn ? a.steps() : a.stepsEs();
    return new AchievementView(
        a.key(),
        a.title(),
        desc,
        a.category(),
        a.docUrl(),
        status,
        count,
        target,
        via,
        steps,
        guide.repos());
  }
}
