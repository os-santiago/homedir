package com.scanales.homedir.reputation.bounty;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bounty Hunter levels based on accumulated points.
 * Each level unlocks specific rewards (frames, badges, etc.).
 */
public enum BountyHunterLevel {
  NONE(0L, "None", null),
  NOVICE(50L, "Novice Bounty Hunter", "bounty-hunter-novice-frame"),
  EXPERIENCED(150L, "Experienced Bounty Hunter", "bounty-hunter-experienced-frame"),
  PROFESSIONAL(400L, "Professional Bounty Hunter", "bounty-hunter-professional-frame"),
  ULTIMATE(800L, "Ultimate Bounty Hunter", "bounty-hunter-ultimate-frame"),
  TRANSCENDENTAL(1500L, "Transcendental Bounty Hunter", "bounty-hunter-transcendental-frame");

  private final long minPoints;
  private final String displayName;
  private final String rewardFrameId;

  BountyHunterLevel(long minPoints, String displayName, String rewardFrameId) {
    this.minPoints = minPoints;
    this.displayName = displayName;
    this.rewardFrameId = rewardFrameId;
  }

  @JsonProperty("min_points")
  public long getMinPoints() {
    return minPoints;
  }

  @JsonProperty("display_name")
  public String getDisplayName() {
    return displayName;
  }

  @JsonProperty("reward_frame_id")
  public String getRewardFrameId() {
    return rewardFrameId;
  }

  /**
   * Determine the appropriate level for a given point total.
   * Returns the highest level the user has achieved.
   */
  public static BountyHunterLevel fromPoints(long points) {
    if (points < NOVICE.minPoints) {
      return NONE;
    }
    if (points < EXPERIENCED.minPoints) {
      return NOVICE;
    }
    if (points < PROFESSIONAL.minPoints) {
      return EXPERIENCED;
    }
    if (points < ULTIMATE.minPoints) {
      return PROFESSIONAL;
    }
    if (points < TRANSCENDENTAL.minPoints) {
      return ULTIMATE;
    }
    return TRANSCENDENTAL;
  }
}
