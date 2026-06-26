package com.scanales.homedir.reputation.bounty;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BountyHunterLevelTest {

  @Test
  void fromPoints_returnsNoneForZeroPoints() {
    assertEquals(BountyHunterLevel.NONE, BountyHunterLevel.fromPoints(0L));
  }

  @Test
  void fromPoints_returnsNoviceFor50Points() {
    assertEquals(BountyHunterLevel.NOVICE, BountyHunterLevel.fromPoints(50L));
    assertEquals(BountyHunterLevel.NOVICE, BountyHunterLevel.fromPoints(100L));
  }

  @Test
  void fromPoints_returnsExperiencedFor150Points() {
    assertEquals(BountyHunterLevel.EXPERIENCED, BountyHunterLevel.fromPoints(150L));
    assertEquals(BountyHunterLevel.EXPERIENCED, BountyHunterLevel.fromPoints(300L));
  }

  @Test
  void fromPoints_returnsProfessionalFor400Points() {
    assertEquals(BountyHunterLevel.PROFESSIONAL, BountyHunterLevel.fromPoints(400L));
    assertEquals(BountyHunterLevel.PROFESSIONAL, BountyHunterLevel.fromPoints(700L));
  }

  @Test
  void fromPoints_returnsUltimateFor800Points() {
    assertEquals(BountyHunterLevel.ULTIMATE, BountyHunterLevel.fromPoints(800L));
    assertEquals(BountyHunterLevel.ULTIMATE, BountyHunterLevel.fromPoints(1400L));
  }

  @Test
  void fromPoints_returnsTranscendentalFor1500PlusPoints() {
    assertEquals(BountyHunterLevel.TRANSCENDENTAL, BountyHunterLevel.fromPoints(1500L));
    assertEquals(BountyHunterLevel.TRANSCENDENTAL, BountyHunterLevel.fromPoints(10000L));
  }

  @Test
  void fromPoints_handlesEdgeCases() {
    assertEquals(BountyHunterLevel.NONE, BountyHunterLevel.fromPoints(49L));
    assertEquals(BountyHunterLevel.NOVICE, BountyHunterLevel.fromPoints(149L));
    assertEquals(BountyHunterLevel.EXPERIENCED, BountyHunterLevel.fromPoints(399L));
    assertEquals(BountyHunterLevel.PROFESSIONAL, BountyHunterLevel.fromPoints(799L));
    assertEquals(BountyHunterLevel.ULTIMATE, BountyHunterLevel.fromPoints(1499L));
  }

  @Test
  void getters_returnCorrectValues() {
    assertEquals(50L, BountyHunterLevel.NOVICE.getMinPoints());
    assertEquals("Novice Bounty Hunter", BountyHunterLevel.NOVICE.getDisplayName());
    assertEquals("bounty-hunter-novice-frame", BountyHunterLevel.NOVICE.getRewardFrameId());

    assertEquals(150L, BountyHunterLevel.EXPERIENCED.getMinPoints());
    assertEquals("Experienced Bounty Hunter", BountyHunterLevel.EXPERIENCED.getDisplayName());

    assertEquals(400L, BountyHunterLevel.PROFESSIONAL.getMinPoints());
    assertEquals(800L, BountyHunterLevel.ULTIMATE.getMinPoints());
    assertEquals(1500L, BountyHunterLevel.TRANSCENDENTAL.getMinPoints());
  }

  @Test
  void none_hasNullReward() {
    assertNull(BountyHunterLevel.NONE.getRewardFrameId());
  }
}
