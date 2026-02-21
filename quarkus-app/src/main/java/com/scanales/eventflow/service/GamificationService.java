package com.scanales.eventflow.service;

import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.economy.EconomyService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.Locale;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GamificationService {

  private static final Logger LOG = Logger.getLogger(GamificationService.class);

  @Inject UserProfileService userProfiles;
  @Inject EconomyService economyService;

  public boolean award(String userId, GamificationActivity activity) {
    return award(userId, activity, null);
  }

  public boolean award(String userId, GamificationActivity activity, String reference) {
    if (userId == null || userId.isBlank() || activity == null) {
      return false;
    }
    UserProfile profile = ensureProfile(userId);
    if (profile == null) {
      return false;
    }

    String title = formatTitle(activity, reference);
    if (activity.onceEver() && profile.hasHistoryTitle(title)) {
      return false;
    }

    String today = LocalDate.now().toString();
    if (activity.oncePerDay()) {
      String dailyKey = dailyKey(activity, reference);
      if (today.equals(profile.getActivityDailyStamps().get(dailyKey))) {
        return false;
      }
      profile.getActivityDailyStamps().put(dailyKey, today);
      if (activity == GamificationActivity.DAILY_CHECKIN) {
        profile.setLastDailyCheckinDate(today);
      }
      userProfiles.update(profile);
    }

    userProfiles.addXp(profile.getUserId(), activity.xp(), title, activity.questClass());
    try {
      economyService.rewardFromGamification(profile.getUserId(), activity.key(), activity.xp(), reference);
    } catch (EconomyService.CapacityException e) {
      LOG.warnf("gamification_reward_blocked user=%s code=%s", profile.getUserId(), e.getMessage());
    } catch (Exception e) {
      LOG.warnf(e, "gamification_reward_failed user=%s", profile.getUserId());
    }
    return true;
  }

  private UserProfile ensureProfile(String userId) {
    String normalized = userId.trim().toLowerCase(Locale.ROOT);
    return userProfiles
        .find(normalized)
        .orElseGet(() -> userProfiles.upsert(normalized, normalized, normalized));
  }

  private static String formatTitle(GamificationActivity activity, String reference) {
    if (reference == null || reference.isBlank()) {
      return activity.title();
    }
    return activity.title() + " · " + reference.trim();
  }

  private static String dailyKey(GamificationActivity activity, String reference) {
    String key = activity.key();
    if (reference == null || reference.isBlank()) {
      return key;
    }
    return key + ":" + reference.trim().toLowerCase(Locale.ROOT);
  }
}
