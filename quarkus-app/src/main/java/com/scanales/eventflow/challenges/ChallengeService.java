package com.scanales.eventflow.challenges;

import com.scanales.eventflow.economy.EconomyService;
import com.scanales.eventflow.model.GamificationActivity;
import com.scanales.eventflow.model.UserProfile;
import com.scanales.eventflow.service.PersistenceService;
import com.scanales.eventflow.service.UserProfileService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ChallengeService {

  private static final Logger LOG = Logger.getLogger(ChallengeService.class);
  private static final String OPEN_SOURCE_IDENTITY_ID = "open-source-identity";

  @Inject PersistenceService persistenceService;
  @Inject UserProfileService userProfileService;
  @Inject EconomyService economyService;

  private final Object stateLock = new Object();
  private final Map<String, Map<String, ChallengeProgress>> progressByUser = new LinkedHashMap<>();
  private volatile long lastKnownStateMtime = Long.MIN_VALUE;

  @PostConstruct
  void init() {
    synchronized (stateLock) {
      refreshFromDisk(true);
    }
  }

  public List<ChallengeDefinition> catalog() {
    return ChallengeCatalog.definitions();
  }

  public ChallengeActivityResult recordActivity(String userId, GamificationActivity activity) {
    String normalizedUserId = normalizeUserId(userId);
    if (normalizedUserId == null || activity == null) {
      return ChallengeActivityResult.empty();
    }
    synchronized (stateLock) {
      refreshFromDisk(false);
      boolean changed = false;
      List<String> completed = new ArrayList<>();
      List<String> rewarded = new ArrayList<>();
      Map<String, ChallengeProgress> userState = userProgress(normalizedUserId);

      ChallengeMutation baseline = applyDerivedOpenSourceIdentity(normalizedUserId, userState);
      changed |= baseline.changed();
      completed.addAll(baseline.completedIds());
      rewarded.addAll(baseline.rewardedIds());

      String activityKey = activity.key();
      Instant now = Instant.now();
      for (ChallengeDefinition definition : catalog()) {
        Integer target = definition.activityTargets().get(activityKey);
        if (target == null || target <= 0) {
          continue;
        }
        ChallengeProgress current = userState.getOrDefault(definition.id(), ChallengeProgress.empty(definition.id()));
        int existingCount = current.activityCounts().getOrDefault(activityKey, 0);
        int updatedCount = Math.min(target, existingCount + 1);
        boolean progressChanged = updatedCount != existingCount;
        ChallengeProgress next = current;
        if (progressChanged) {
          Map<String, Integer> counts = new LinkedHashMap<>(current.activityCounts());
          counts.put(activityKey, updatedCount);
          Instant startedAt = current.startedAt() != null ? current.startedAt() : now;
          next =
              new ChallengeProgress(
                  definition.id(),
                  counts,
                  startedAt,
                  now,
                  current.completedAt(),
                  current.rewardGrantedAt());
        }
        ChallengeMutation completion = ensureCompletionAndReward(normalizedUserId, definition, next, now);
        next = completion.progress();
        if (progressChanged || completion.changed()) {
          userState.put(definition.id(), next);
          changed = true;
        }
        completed.addAll(completion.completedIds());
        rewarded.addAll(completion.rewardedIds());
      }

      if (changed) {
        persistAsync();
      }
      return new ChallengeActivityResult(List.copyOf(completed), List.copyOf(rewarded));
    }
  }

  public List<ChallengeProgressCard> listProgressForUser(String userId) {
    String normalizedUserId = normalizeUserId(userId);
    if (normalizedUserId == null) {
      return List.of();
    }
    synchronized (stateLock) {
      refreshFromDisk(false);
      Map<String, ChallengeProgress> userState = userProgress(normalizedUserId);
      ChallengeMutation baseline = applyDerivedOpenSourceIdentity(normalizedUserId, userState);
      if (baseline.changed()) {
        persistAsync();
      }

      List<ChallengeProgressCard> cards = new ArrayList<>();
      for (ChallengeDefinition definition : catalog()) {
        ChallengeProgress progress =
            userState.getOrDefault(definition.id(), ChallengeProgress.empty(definition.id()));
        int completedSteps = completedSteps(progress, definition);
        cards.add(
            new ChallengeProgressCard(
                definition.id(),
                definition.title(),
                definition.description(),
                definition.rewardHcoin(),
                definition.activityTargets(),
                progress.activityCounts(),
                completedSteps,
                definition.totalSteps(),
                progress.completedAt() != null,
                progress.startedAt(),
                progress.updatedAt(),
                progress.completedAt(),
                progress.rewardGrantedAt()));
      }
      return List.copyOf(cards);
    }
  }

  public void resetForTests() {
    synchronized (stateLock) {
      progressByUser.clear();
      ChallengeStateSnapshot snapshot = ChallengeStateSnapshot.empty();
      persistenceService.saveChallengeStateSync(snapshot);
      lastKnownStateMtime = persistenceService.challengeStateLastModifiedMillis();
    }
  }

  private void refreshFromDisk(boolean force) {
    long diskMtime = persistenceService.challengeStateLastModifiedMillis();
    if (!force && diskMtime == lastKnownStateMtime) {
      return;
    }
    applySnapshot(persistenceService.loadChallengeState().orElse(ChallengeStateSnapshot.empty()));
    lastKnownStateMtime = diskMtime;
  }

  private void applySnapshot(ChallengeStateSnapshot snapshot) {
    progressByUser.clear();
    for (Map.Entry<String, List<ChallengeProgress>> entry : snapshot.progressByUser().entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        continue;
      }
      Map<String, ChallengeProgress> byChallenge = new LinkedHashMap<>();
      for (ChallengeProgress progress : entry.getValue()) {
        if (progress == null || progress.challengeId() == null || progress.challengeId().isBlank()) {
          continue;
        }
        byChallenge.put(progress.challengeId(), progress);
      }
      progressByUser.put(entry.getKey(), byChallenge);
    }
  }

  private void persistAsync() {
    persistenceService.saveChallengeState(toSnapshot());
    lastKnownStateMtime = persistenceService.challengeStateLastModifiedMillis();
  }

  private ChallengeStateSnapshot toSnapshot() {
    Map<String, List<ChallengeProgress>> snapshot = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, ChallengeProgress>> entry : progressByUser.entrySet()) {
      snapshot.put(entry.getKey(), List.copyOf(entry.getValue().values()));
    }
    return new ChallengeStateSnapshot(ChallengeStateSnapshot.SCHEMA_VERSION, Instant.now(), snapshot);
  }

  private Map<String, ChallengeProgress> userProgress(String userId) {
    return progressByUser.computeIfAbsent(userId, ignored -> new LinkedHashMap<>());
  }

  private ChallengeMutation applyDerivedOpenSourceIdentity(String userId, Map<String, ChallengeProgress> userState) {
    ChallengeDefinition definition = ChallengeCatalog.find(OPEN_SOURCE_IDENTITY_ID);
    if (definition == null) {
      return ChallengeMutation.noChange(null);
    }
    UserProfile profile = userProfileService.find(userId).orElse(null);
    if (profile == null) {
      return ChallengeMutation.noChange(userState.getOrDefault(definition.id(), ChallengeProgress.empty(definition.id())));
    }
    ChallengeProgress current = userState.getOrDefault(definition.id(), ChallengeProgress.empty(definition.id()));
    Map<String, Integer> counts = new LinkedHashMap<>(current.activityCounts());
    boolean changed = false;
    changed |= applyDerivedCount(counts, "first_login_bonus", 1);
    if (profile.getGithub() != null) {
      changed |= applyDerivedCount(counts, "github_linked", 1);
    }
    if (profile.getDiscord() != null) {
      changed |= applyDerivedCount(counts, "discord_linked", 1);
    }
    ChallengeProgress next = current;
    if (changed) {
      Instant now = Instant.now();
      next =
          new ChallengeProgress(
              definition.id(),
              counts,
              current.startedAt() != null ? current.startedAt() : now,
              now,
              current.completedAt(),
              current.rewardGrantedAt());
    }
    ChallengeMutation completion = ensureCompletionAndReward(userId, definition, next, Instant.now());
    next = completion.progress();
    if (changed || completion.changed()) {
      userState.put(definition.id(), next);
    }
    return new ChallengeMutation(
        changed || completion.changed(),
        next,
        completion.completedIds(),
        completion.rewardedIds());
  }

  private static boolean applyDerivedCount(Map<String, Integer> counts, String key, int target) {
    int current = counts.getOrDefault(key, 0);
    if (current >= target) {
      return false;
    }
    counts.put(key, target);
    return true;
  }

  private ChallengeMutation ensureCompletionAndReward(
      String userId, ChallengeDefinition definition, ChallengeProgress progress, Instant now) {
    if (definition == null || progress == null) {
      return ChallengeMutation.noChange(progress);
    }
    boolean completed = isCompleted(progress, definition);
    ChallengeProgress next = progress;
    boolean changed = false;
    List<String> completedIds = List.of();
    List<String> rewardedIds = List.of();

    if (completed && progress.completedAt() == null) {
      next =
          new ChallengeProgress(
              progress.challengeId(),
              progress.activityCounts(),
              progress.startedAt() != null ? progress.startedAt() : now,
              now,
              now,
              progress.rewardGrantedAt());
      changed = true;
      completedIds = List.of(definition.id());
    }

    if (completed && next.rewardGrantedAt() == null) {
      try {
        EconomyService.RewardResult reward =
            economyService.rewardChallengeCompletion(userId, definition.id(), definition.rewardHcoin());
        if (reward.awarded() || reward.amountHcoin() == 0L) {
          next =
              new ChallengeProgress(
                  next.challengeId(),
                  next.activityCounts(),
                  next.startedAt(),
                  now,
                  next.completedAt() != null ? next.completedAt() : now,
                  now);
          changed = true;
          rewardedIds = List.of(definition.id());
        }
      } catch (EconomyService.CapacityException e) {
        LOG.warnf("challenge_reward_blocked user=%s challenge=%s code=%s", userId, definition.id(), e.getMessage());
      } catch (Exception e) {
        LOG.warnf(e, "challenge_reward_failed user=%s challenge=%s", userId, definition.id());
      }
    }

    return new ChallengeMutation(changed, next, completedIds, rewardedIds);
  }

  private static boolean isCompleted(ChallengeProgress progress, ChallengeDefinition definition) {
    for (Map.Entry<String, Integer> entry : definition.activityTargets().entrySet()) {
      if (progress.activityCounts().getOrDefault(entry.getKey(), 0) < entry.getValue()) {
        return false;
      }
    }
    return !definition.activityTargets().isEmpty();
  }

  private static int completedSteps(ChallengeProgress progress, ChallengeDefinition definition) {
    int completed = 0;
    for (Map.Entry<String, Integer> entry : definition.activityTargets().entrySet()) {
      completed += Math.min(entry.getValue(), progress.activityCounts().getOrDefault(entry.getKey(), 0));
    }
    return completed;
  }

  private static String normalizeUserId(String userId) {
    if (userId == null) {
      return null;
    }
    String normalized = userId.trim().toLowerCase(Locale.ROOT);
    return normalized.isBlank() ? null : normalized;
  }

  public record ChallengeActivityResult(List<String> completedChallengeIds, List<String> rewardedChallengeIds) {
    static ChallengeActivityResult empty() {
      return new ChallengeActivityResult(List.of(), List.of());
    }
  }

  public record ChallengeProgressCard(
      String id,
      String title,
      String description,
      int rewardHcoin,
      Map<String, Integer> activityTargets,
      Map<String, Integer> activityCounts,
      int completedSteps,
      int totalSteps,
      boolean completed,
      Instant startedAt,
      Instant updatedAt,
      Instant completedAt,
      Instant rewardGrantedAt) {
  }

  private record ChallengeMutation(
      boolean changed,
      ChallengeProgress progress,
      List<String> completedIds,
      List<String> rewardedIds) {
    static ChallengeMutation noChange(ChallengeProgress progress) {
      return new ChallengeMutation(false, progress, List.of(), List.of());
    }
  }
}
