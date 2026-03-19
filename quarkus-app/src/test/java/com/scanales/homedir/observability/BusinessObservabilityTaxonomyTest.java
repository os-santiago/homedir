package com.scanales.homedir.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BusinessObservabilityTaxonomyTest {

  @Test
  void mapsRoutesToModules() {
    assertEquals("home", BusinessObservabilityTaxonomy.moduleForRoute("/"));
    assertEquals("community", BusinessObservabilityTaxonomy.moduleForRoute("/comunidad"));
    assertEquals("events", BusinessObservabilityTaxonomy.moduleForRoute("/event/devopsdays"));
    assertEquals("project", BusinessObservabilityTaxonomy.moduleForRoute("/proyectos"));
    assertEquals("profile", BusinessObservabilityTaxonomy.moduleForRoute("/u/scanalesespinoza"));
    assertEquals("admin", BusinessObservabilityTaxonomy.moduleForRoute("/private/admin/metrics"));
    assertEquals("info", BusinessObservabilityTaxonomy.moduleForRoute("/privacy-policy"));
    assertEquals("other", BusinessObservabilityTaxonomy.moduleForRoute("/something-else"));
  }

  @Test
  void canonicalizesKnownActions() {
    assertEquals(
        "community_vote", BusinessObservabilityTaxonomy.canonicalAction("community.vote.must_see"));
    assertEquals(
        "community_lightning_post",
        BusinessObservabilityTaxonomy.canonicalAction("community.lightning.thread.create"));
    assertEquals("cfp_submit", BusinessObservabilityTaxonomy.canonicalAction("cfp.submission.create"));
    assertEquals(
        "volunteer_selected",
        BusinessObservabilityTaxonomy.canonicalAction("volunteer.submission.status.accepted"));
    assertEquals("profile", BusinessObservabilityTaxonomy.moduleForAction("login_success"));
  }

  @Test
  void rejectsUnsafeActions() {
    assertNull(BusinessObservabilityTaxonomy.canonicalAction(" "));
    assertNull(BusinessObservabilityTaxonomy.canonicalAction("????"));
  }
}
