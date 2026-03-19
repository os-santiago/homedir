package com.scanales.homedir.challenges;

import com.scanales.homedir.economy.EconomyService;
import com.scanales.homedir.model.GamificationActivity;
import com.scanales.homedir.model.UserProfile;
import com.scanales.homedir.service.PersistenceService;
import com.scanales.homedir.service.UserProfileService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
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
      List<String> started = new ArrayList<>();
      List<String> completed = new ArrayList<>();
      List<String> rewarded = new ArrayList<>();
      Map<String, ChallengeProgress> userState = userProgress(normalizedUserId);

      ChallengeMutation baseline = applyDerivedOpenSourceIdentity(normalizedUserId, userState);
      changed |= baseline.changed();
      started.addAll(baseline.startedIds());
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
        if (progressChanged && current.startedAt() == null && next.startedAt() != null) {
          started.add(definition.id());
        }
        if (progressChanged || completion.changed()) {
          userState.put(definition.id(), next);
          changed = true;
        }
        started.addAll(completion.startedIds());
        completed.addAll(completion.completedIds());
        rewarded.addAll(completion.rewardedIds());
      }

      if (changed) {
        persistAsync();
      }
      return new ChallengeActivityResult(
          List.copyOf(started), List.copyOf(completed), List.copyOf(rewarded));
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

  public List<ChallengeTrend> trendingChallenges(Duration window, int limit) {
    int cappedLimit = Math.max(1, Math.min(limit, 10));
    Instant cutoff =
        (window == null || window.isNegative() || window.isZero())
            ? Instant.EPOCH
            : Instant.now().minus(window);
    synchronized (stateLock) {
      refreshFromDisk(false);
      Map<String, Long> completionsByChallenge = new LinkedHashMap<>();
      Map<String, Instant> latestCompletionByChallenge = new LinkedHashMap<>();
      for (Map<String, ChallengeProgress> userState : progressByUser.values()) {
        for (ChallengeProgress progress : userState.values()) {
          if (progress == null || progress.challengeId() == null || progress.completedAt() == null) {
            continue;
          }
          if (progress.completedAt().isBefore(cutoff)) {
            continue;
          }
          completionsByChallenge.merge(progress.challengeId(), 1L, Long::sum);
          latestCompletionByChallenge.merge(
              progress.challengeId(), progress.completedAt(), (left, right) -> left.isAfter(right) ? left : right);
        }
      }
      return completionsByChallenge.entrySet().stream()
          .map(
              entry -> {
                ChallengeDefinition definition = ChallengeCatalog.find(entry.getKey());
                return new ChallengeTrend(
                    entry.getKey(),
                    definition != null ? definition.rewardHcoin() : 0,
                    entry.getValue(),
                    latestCompletionByChallenge.get(entry.getKey()));
              })
          .sorted(
              java.util.Comparator.comparingLong(ChallengeTrend::completions).reversed()
                  .thenComparing(ChallengeTrend::latestCompletedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                  .thenComparing(ChallengeTrend::challengeId))
          .limit(cappedLimit)
          .toList();
    }
  }

  public ChallengeLeaderboard leaderboardForUser(String userId) {
    String normalizedUserId = normalizeUserId(userId);
    if (normalizedUserId == null) {
      return new ChallengeLeaderboard(0, 0, 0);
    }
    synchronized (stateLock) {
      refreshFromDisk(false);
      List<UserChallengeRank> ranking = progressByUser.entrySet().stream()
          .map(entry -> new UserChallengeRank(entry.getKey(), completedCount(entry.getValue())))
          .filter(entry -> entry.completedCount() > 0)
          .sorted(
              java.util.Comparator.comparingInt(UserChallengeRank::completedCount).reversed()
                  .thenComparing(UserChallengeRank::userId))
          .toList();
      int completed = ranking.stream()
          .filter(entry -> normalizedUserId.equals(entry.userId()))
          .mapToInt(UserChallengeRank::completedCount)
          .findFirst()
          .orElse(0);
      int rank = 0;
      for (int i = 0; i < ranking.size(); i++) {
        if (normalizedUserId.equals(ranking.get(i).userId())) {
          rank = i + 1;
          break;
        }
      }
      return new ChallengeLeaderboard(rank, ranking.size(), completed);
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
        changed && current.startedAt() == null && next.startedAt() != null ? List.of(definition.id()) : List.of(),
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
        LOG.warnf("challenge_reward_blocked code=%s", safeLogCode(e.getMessage()));
      } catch (Exception e) {
        LOG.warn("challenge_reward_failed", e);
      }
    }

    return new ChallengeMutation(changed, next, List.of(), completedIds, rewardedIds);
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

  private static String safeLogCode(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    String normalized =
        value
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]", "_");
    return normalized.isBlank() ? "unknown" : normalized;
  }

  public record ChallengeActivityResult(
      List<String> startedChallengeIds, List<String> completedChallengeIds, List<String> rewardedChallengeIds) {
    static ChallengeActivityResult empty() {
      return new ChallengeActivityResult(List.of(), List.of(), List.of());
    }
  }

  public record ChallengeTrend(
      String challengeId, int rewardHcoin, long completions, Instant latestCompletedAt) {}

  public record ChallengeLeaderboard(int rank, int activeMembers, int completedChallenges) {}

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
      List<String> startedIds,
      List<String> completedIds,
      List<String> rewardedIds) {
    static ChallengeMutation noChange(ChallengeProgress progress) {
      return new ChallengeMutation(false, progress, List.of(), List.of(), List.of());
    }
  }

  private static int completedCount(Map<String, ChallengeProgress> userState) {
    if (userState == null || userState.isEmpty()) {
      return 0;
    }
    int completed = 0;
    for (ChallengeProgress progress : userState.values()) {
      if (progress != null && progress.completedAt() != null) {
        completed++;
      }
    }
    return completed;
  }

  private record UserChallengeRank(String userId, int completedCount) {}
}
