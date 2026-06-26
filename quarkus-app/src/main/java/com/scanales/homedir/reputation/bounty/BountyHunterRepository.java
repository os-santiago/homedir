package com.scanales.homedir.reputation.bounty;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository for Bounty Hunter scores and events.
 * In-memory implementation for initial phase.
 */
@ApplicationScoped
public class BountyHunterRepository {

  private final Map<String, BountyHunterScore> scores = new ConcurrentHashMap<>();
  private final List<BountyHunterEvent> events = new ArrayList<>();

  public Optional<BountyHunterScore> findScoreByUserId(String userId) {
    return Optional.ofNullable(scores.get(userId));
  }

  public void saveScore(BountyHunterScore score) {
    scores.put(score.userId(), score);
  }

  public void appendEvent(BountyHunterEvent event) {
    synchronized (events) {
      events.add(event);
    }
  }

  public List<BountyHunterEvent> findEventsByUserId(String userId) {
    synchronized (events) {
      return events.stream()
          .filter(e -> e.userId().equals(userId))
          .sorted(Comparator.comparing(BountyHunterEvent::timestamp).reversed())
          .toList();
    }
  }

  public List<BountyHunterScore> findTopScores(int limit) {
    return scores.values().stream()
        .sorted(
            Comparator.comparing(BountyHunterScore::totalPoints)
                .reversed()
                .thenComparing(BountyHunterScore::updatedAt))
        .limit(limit)
        .toList();
  }

  public Optional<Integer> getUserRank(String userId) {
    if (userId == null || userId.isBlank()) {
      return Optional.empty();
    }
    String normalizedUserId = userId.trim().toLowerCase();
    List<BountyHunterScore> sortedScores =
        scores.values().stream()
            .sorted(
                Comparator.comparing(BountyHunterScore::totalPoints)
                    .reversed()
                    .thenComparing(BountyHunterScore::updatedAt))
            .toList();

    for (int i = 0; i < sortedScores.size(); i++) {
      if (normalizedUserId.equals(sortedScores.get(i).userId())) {
        return Optional.of(i + 1);
      }
    }
    return Optional.empty();
  }

  public long getTotalUsersCount() {
    return scores.size();
  }
}
