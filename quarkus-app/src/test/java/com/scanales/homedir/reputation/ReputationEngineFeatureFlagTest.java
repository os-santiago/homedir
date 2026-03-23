package com.scanales.homedir.reputation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.scanales.homedir.TestDataDir;
import com.scanales.homedir.service.PersistenceService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestDataDir.class)
class ReputationEngineFeatureFlagTest {

  @Inject ReputationEngineService reputationEngineService;
  @Inject PersistenceService persistenceService;

  @BeforeEach
  void setUp() {
    reputationEngineService.resetForTests();
    persistenceService.flush();
  }

  @Test
  void doesNotIngestWhenEngineFlagIsDisabled() {
    assertFalse(reputationEngineService.trackQuestCompleted("alice@example.com", "challenge-disabled"));
    assertEquals(0, reputationEngineService.snapshot().eventsById().size());
  }
}
