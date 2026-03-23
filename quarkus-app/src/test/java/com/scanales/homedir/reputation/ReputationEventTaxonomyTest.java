package com.scanales.homedir.reputation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReputationEventTaxonomyTest {

  @Test
  void taxonomyContainsExpectedMvpEvents() {
    assertTrue(ReputationEventTaxonomy.find("quest_completed").isPresent());
    assertTrue(ReputationEventTaxonomy.find("event_attended").isPresent());
    assertTrue(ReputationEventTaxonomy.find("event_speaker").isPresent());
    assertTrue(ReputationEventTaxonomy.find("content_published").isPresent());
    assertTrue(ReputationEventTaxonomy.find("content_recommended").isPresent());
    assertTrue(ReputationEventTaxonomy.find("streak_milestone").isPresent());
    assertTrue(ReputationEventTaxonomy.find("contribution_highlighted").isPresent());
    assertTrue(ReputationEventTaxonomy.find("peer_help_acknowledged").isPresent());
  }

  @Test
  void taxonomyDefinitionsAreUniqueAndWeighted() {
    Set<String> eventTypes = new HashSet<>();
    for (ReputationEventTaxonomy.EventDefinition definition : ReputationEventTaxonomy.definitions()) {
      assertFalse(definition.eventType().isBlank());
      assertTrue(eventTypes.add(definition.eventType()));
      assertTrue(definition.baseWeight() > 0);
      assertFalse(definition.sourceSignals().isEmpty());
    }
    assertEquals(ReputationEventTaxonomy.definitions().size(), eventTypes.size());
  }
}
