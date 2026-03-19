package com.scanales.eventflow.service;

import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.challenges.ChallengeService;
import com.scanales.eventflow.economy.EconomyService;
import com.scanales.eventflow.notifications.Notification;
import com.scanales.eventflow.notifications.NotificationService;
import com.scanales.eventflow.notifications.NotificationType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.Locale;
import java.util.ResourceBundle;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GamificationService {

  private static final Logger LOG = Logger.getLogger(GamificationService.class);

  @Inject UserProfileService userProfiles;
  @Inject EconomyService economyService;
  @Inject ChallengeService challengeService;
  @Inject NotificationService notificationService;
  @Inject UsageMetricsService metrics;

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
    try {
      ChallengeService.ChallengeActivityResult challengeResult =
          challengeService.recordActivity(profile.getUserId(), activity);
      if (challengeResult != null) {
        challengeResult
            .startedChallengeIds()
            .forEach(
                challengeId -> {
                  metrics.recordFunnelStep("challenge.started");
                  metrics.recordFunnelStep("challenge.started." + challengeId);
                });
        challengeResult
            .completedChallengeIds()
            .forEach(
                challengeId -> {
                  metrics.recordFunnelStep("challenge.completed");
                  metrics.recordFunnelStep("challenge.completed." + challengeId);
                  enqueueChallengeCompletionNotification(profile, challengeId);
                });
      }
    } catch (Exception e) {
      LOG.warnf(e, "challenge_progress_failed user=%s activity=%s", profile.getUserId(), activity.key());
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

  private void enqueueChallengeCompletionNotification(UserProfile profile, String challengeId) {
    if (profile == null || profile.getUserId() == null || profile.getUserId().isBlank()) {
      return;
    }
    String localeCode =
        profile.getPreferredLocale() == null || profile.getPreferredLocale().isBlank()
            ? "es"
            : profile.getPreferredLocale();
    ResourceBundle bundle = localizedBundle(localeCode);
    Notification notification = new Notification();
    notification.userId = profile.getUserId();
    notification.type = NotificationType.SOCIAL;
    notification.talkId = "challenge-completion";
    notification.title = bundleText(bundle, "challenge_notification_title");
    notification.message =
        formatNamed(
            bundleText(bundle, "challenge_notification_message"),
            "title",
            localizedChallengeTitle(challengeId, bundle));
    notification.dedupeKey = "challenge.complete:" + safeChallengeKey(challengeId);
    notificationService.enqueue(notification);
  }

  private ResourceBundle localizedBundle(String localeCode) {
    String normalized = localeCode == null ? "" : localeCode.trim().toLowerCase(Locale.ROOT);
    Locale bundleLocale =
        normalized.startsWith("es") ? Locale.forLanguageTag("es") : Locale.ROOT;
    return ResourceBundle.getBundle("i18n", bundleLocale);
  }

  private static String bundleText(ResourceBundle bundle, String key) {
    return bundle.containsKey(key) ? bundle.getString(key) : key;
  }

  private static String localizedChallengeTitle(String challengeId, ResourceBundle bundle) {
    if (challengeId == null || challengeId.isBlank()) {
      return "challenge";
    }
    return switch (challengeId) {
      case "community-scout" -> bundleText(bundle, "challenge_community_scout_title");
      case "event-explorer" -> bundleText(bundle, "challenge_event_explorer_title");
      case "open-source-identity" -> bundleText(bundle, "challenge_open_source_identity_title");
      default -> challengeId;
    };
  }

  private static String formatNamed(String pattern, Object... keyValues) {
    String formatted = pattern;
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      String key = String.valueOf(keyValues[i]);
      String value = String.valueOf(keyValues[i + 1]);
      formatted = formatted.replace("{" + key + "}", value);
    }
    return formatted;
  }

  private static String safeChallengeKey(String challengeId) {
    if (challengeId == null || challengeId.isBlank()) {
      return "challenge";
    }
    return challengeId.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
  }
}
