package com.scanales.homedir.public_;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class AboutResourceVisibilityLogicTest {

  @Test
  void commitHashIsHiddenInProductionForNonAdmins() {
    assertFalse(AboutResource.shouldShowCommitHash("prod", false));
  }

  @Test
  void commitHashIsShownInProductionForAdmins() {
    assertTrue(AboutResource.shouldShowCommitHash("prod", true));
  }

  @Test
  void commitHashIsShownOutsideProduction() {
    assertTrue(AboutResource.shouldShowCommitHash("test", false));
    assertTrue(AboutResource.shouldShowCommitHash("dev", false));
  }

  @Test
  void authConfigIsOnlyShownToAdmins() {
    assertFalse(AboutResource.shouldShowAuthConfig(false));
    assertTrue(AboutResource.shouldShowAuthConfig(true));
  }
}
