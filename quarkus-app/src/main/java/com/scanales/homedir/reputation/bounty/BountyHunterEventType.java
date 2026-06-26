package com.scanales.homedir.reputation.bounty;

/** Types of Bounty Hunter events that can trigger point awards. */
public enum BountyHunterEventType {
  /** Issue created and awaiting validation */
  ISSUE_CREATED,

  /** Issue validated and labeled by an administrator */
  ISSUE_VALIDATED,

  /** Issue label approved by administrator, points awarded to creator */
  ISSUE_LABEL_APPROVED,

  /** Issue resolved by an approved pull request */
  ISSUE_RESOLVED_BY_PR,

  /** Pull request approved by maintainer */
  PR_APPROVED,

  /** Reward unlocked (frame, badge, etc.) */
  REWARD_UNLOCKED
}
