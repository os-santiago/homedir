package com.scanales.homedir.reputation.bounty;

/**
 * Types of events that trigger Bounty Hunter point awards.
 */
public enum BountyHunterEventType {
  /** Issue label approved by administrator, points awarded to creator */
  ISSUE_LABEL_APPROVED,

  /** Issue resolved by an approved pull request, points awarded to resolver */
  ISSUE_RESOLVED_BY_PR
}
