package com.scanales.homedir.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scanales.homedir.model.UserProfile;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileReadinessServiceTest {

  private final ProfileReadinessService service = new ProfileReadinessService();

  @Test
  void readinessIsCompleteWhenMinimumProfileFieldsArePresent() {
    UserProfile profile = new UserProfile();
    profile.setSpeakerProfile(
        new UserProfile.SpeakerProfile(
            true,
            "Community speaker",
            "I share practical lessons from community delivery and platform work.",
            "Homedir",
            "https://homedir.dev",
            "https://linkedin.com/in/example",
            "community,delivery",
            Instant.now(),
            Instant.now()));

    ProfileReadinessService.Readiness readiness =
        service.evaluate(profile, "Ready User", "https://example.com/avatar.png");

    assertTrue(readiness.ready());
    assertTrue(readiness.missingFields().isEmpty());
  }

  @Test
  void readinessCollectsMissingFieldsForIncompleteProfiles() {
    UserProfile profile = new UserProfile();
    ProfileReadinessService.Readiness readiness = service.evaluate(profile, " ", null);

    assertFalse(readiness.ready());
    assertEquals(
        List.of(
            ProfileReadinessService.FIELD_NAME,
            ProfileReadinessService.FIELD_AVATAR,
            ProfileReadinessService.FIELD_DESCRIPTION,
            ProfileReadinessService.FIELD_ROLE_OR_INTERESTS),
        readiness.missingFields());
  }
}
