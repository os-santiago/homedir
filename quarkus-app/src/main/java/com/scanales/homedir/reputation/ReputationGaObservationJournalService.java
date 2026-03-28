package com.scanales.homedir.reputation;

import com.scanales.homedir.service.PersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class ReputationGaObservationJournalService {

  @Inject PersistenceService persistenceService;

  private final AtomicReference<ReputationGaObservationJournalSnapshot> snapshotRef =
      new AtomicReference<>(ReputationGaObservationJournalSnapshot.empty());

  @PostConstruct
  void init() {
    snapshotRef.set(
        persistenceService
            .loadReputationGaObservationJournal()
            .orElseGet(ReputationGaObservationJournalSnapshot::empty));
  }

  public ReputationGaObservationJournalSnapshot snapshot() {
    return snapshotRef.get();
  }

  public ReputationGaObservationJournalSnapshot acknowledge(String checkCode, String actor) {
    return update(checkCode, actor, true);
  }

  public ReputationGaObservationJournalSnapshot clear(String checkCode, String actor) {
    return update(checkCode, actor, false);
  }

  private ReputationGaObservationJournalSnapshot update(
      String checkCode, String actor, boolean acknowledged) {
    String normalized = normalizeCheckCode(checkCode);
    ReputationGaObservationJournalSnapshot updated =
        snapshotRef.updateAndGet(current -> apply(current, normalized, acknowledged, actor));
    persistenceService.saveReputationGaObservationJournal(updated);
    return updated;
  }

  private ReputationGaObservationJournalSnapshot apply(
      ReputationGaObservationJournalSnapshot current,
      String checkCode,
      boolean acknowledged,
      String actor) {
    return switch (checkCode) {
      case "hold_weekly_cycle" -> current.withWeeklyCycleObserved(acknowledged, actor);
      case "hold_monthly_cycle" -> current.withMonthlyCycleObserved(acknowledged, actor);
      case "release_window_one" -> current.withReleaseWindowOneObserved(acknowledged, actor);
      case "release_window_two" -> current.withReleaseWindowTwoObserved(acknowledged, actor);
      default -> current;
    };
  }

  public static String normalizeCheckCode(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
