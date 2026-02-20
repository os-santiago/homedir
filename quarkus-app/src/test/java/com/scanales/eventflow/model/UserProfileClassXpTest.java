package com.scanales.eventflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class UserProfileClassXpTest {

  @Test
  void dominantClassUsesClassXpWhenAvailable() {
    UserProfile profile = new UserProfile("test@example.com", "Test", "test@example.com", null);
    profile.addClassXp(QuestClass.MAGE, 25);
    profile.addClassXp(QuestClass.ENGINEER, 40);

    assertEquals(QuestClass.ENGINEER, profile.getDominantQuestClass());
    assertEquals(40, profile.getClassXp(QuestClass.ENGINEER));
    assertEquals(25, profile.getClassXp(QuestClass.MAGE));
  }

  @Test
  void dominantClassFallsBackToLegacyQuestClass() {
    UserProfile profile = new UserProfile("test@example.com", "Test", "test@example.com", null);
    profile.setQuestClass(QuestClass.SCIENTIST);

    assertEquals(QuestClass.SCIENTIST, profile.getDominantQuestClass());
  }

  @Test
  void dominantClassCanBeNullWhenNoSignalExists() {
    UserProfile profile = new UserProfile("test@example.com", "Test", "test@example.com", null);
    assertNull(profile.getDominantQuestClass());
  }
}
