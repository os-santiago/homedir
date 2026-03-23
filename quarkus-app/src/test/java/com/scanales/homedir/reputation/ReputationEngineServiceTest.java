package com.scanales.homedir.reputation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.TestDataDir;
import com.scanales.homedir.service.PersistenceService;
import com.scanales.homedir.service.UserScheduleService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
@TestProfile(ReputationEngineServiceTest.EngineEnabledProfile.class)
class ReputationEngineServiceTest {

  public static class EngineEnabledProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("reputation.engine.enabled", "true");
    }
  }

  @Inject ReputationEngineService reputationEngineService;
  @Inject PersistenceService persistenceService;
  @Inject UserScheduleService userScheduleService;

  @BeforeEach
  void setUp() {
    reputationEngineService.resetForTests();
    userScheduleService.reset();
    persistenceService.flush();
  }

  @Test
  void recordsPhase1EventsWithIdempotencyAndAggregates() {
    assertTrue(reputationEngineService.trackQuestCompleted("alice@example.com", "challenge-1"));
    assertFalse(reputationEngineService.trackQuestCompleted("alice@example.com", "challenge-1"));

    assertTrue(reputationEngineService.trackEventAttended("alice@example.com", "talk-1"));
    assertFalse(reputationEngineService.trackEventAttended("alice@example.com", "talk-1"));

    assertTrue(reputationEngineService.trackContentPublished("alice@example.com", "thread-1"));
    assertTrue(reputationEngineService.trackEventSpeaker("alice@example.com", "submission-1", "event-2026"));
    assertTrue(reputationEngineService.trackEventSpeaker("bob@example.com", "submission-2", "event-2026"));

    persistenceService.flush();
    ReputationEngineService.EngineSnapshot snapshot = reputationEngineService.snapshot();
    assertEquals(5, snapshot.eventsById().size());

    UserReputationAggregate alice = snapshot.aggregatesByUser().get("alice@example.com");
    assertNotNull(alice);
    assertEquals(42L, alice.totalScore());
    assertEquals(42L, alice.weeklyScore());
    assertEquals(42L, alice.monthlyScore());
    assertEquals(42L, alice.risingDelta());
    assertEquals(36L, alice.scoresByDimension().getOrDefault("contribution", 0L));
    assertEquals(6L, alice.scoresByDimension().getOrDefault("participation", 0L));

    UserReputationAggregate bob = snapshot.aggregatesByUser().get("bob@example.com");
    assertNotNull(bob);
    assertEquals(14L, bob.totalScore());
    assertTrue(persistenceService.loadReputationState().isPresent());
  }

  @Test
  void userScheduleRegistrationIngestsEventAttended() {
    assertTrue(userScheduleService.addTalkForUser("carol@example.com", "talk-100"));
    assertFalse(userScheduleService.addTalkForUser("carol@example.com", "talk-100"));

    persistenceService.flush();
    ReputationEngineService.EngineSnapshot snapshot = reputationEngineService.snapshot();
    assertEquals(1, snapshot.eventsById().size());
    assertTrue(
        snapshot.eventsById().values().stream()
            .anyMatch(
                event ->
                    "event_attended".equals(event.eventType())
                        && "carol@example.com".equals(event.actorUserId())
                        && "talk-100".equals(event.sourceObjectId())));
  }
}
