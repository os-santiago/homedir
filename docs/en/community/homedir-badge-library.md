# HomeDir Badge Library

Author: os-santiago

This library defines HomeDir-specific badges grounded in the Reputation Hub and the public signals already visible in the product.

The goal is not to invent vanity labels. The goal is to define a small, measurable badge set that reflects real work contributors can do in HomeDir and that can later be surfaced in profile, reputation, or community views.

## Badge design rules

- Every badge must map to a real HomeDir action or outcome.
- Every badge must be verifiable from repo or product data.
- Every badge must have a clear unlock route and a clear failure mode.
- Every badge must stay understandable without private chat context.
- Every badge should reinforce stability, quality, or community value.

## Badge families

### 1. Builder badges

These badges reward visible product work.

#### First Build

- Represents: a first meaningful contribution that ships.
- Unlock route: open a small issue, implement the fix, add a test, and merge it.
- Evidence: merged PR with linked issue and passing validation.
- Visible effect: marks the contributor as someone who ships useful changes.

#### Builder Signal

- Represents: repeated contribution to visible product surfaces.
- Unlock route: several merged PRs touching product behavior, copy, or navigation.
- Evidence: merged PR history across separate iterations.
- Visible effect: shows the contributor can keep delivering without destabilizing the app.

### 2. Helper badges

These badges reward practical support and review quality.

#### First Review

- Represents: a useful review on a PR that led to an improvement.
- Unlock route: leave actionable feedback and follow it through to resolution.
- Evidence: review thread, resolved comments, and a resulting code change.
- Visible effect: acknowledges review work as part of delivery, not as an afterthought.

#### Helper Signal

- Represents: repeated help that improves another contributor's PR.
- Unlock route: review multiple PRs, focus on risk, and help close the loop.
- Evidence: review activity and resolved threads.
- Visible effect: highlights contributors who reduce ambiguity and regressions.

### 3. Learner badges

These badges reward consistency and growth.

#### First Step

- Represents: starting from a tracked issue and finishing the first real contribution.
- Unlock route: pick one issue, deliver one PR, and close the loop.
- Evidence: issue-to-PR linkage and merge.
- Visible effect: acknowledges the transition from observer to contributor.

#### Consistency Streak

- Represents: repeated, steady participation over time.
- Unlock route: contribute in consecutive periods instead of bursting once.
- Evidence: merged PRs, issue closures, or other visible activity spread across time.
- Visible effect: rewards reliable momentum instead of noisy spikes.

### 4. Coder badges

These badges are based on the Coders section in the Reputation Hub.

#### Code Explorer

- Represents: balanced coding activity across commits, issues, and PRs.
- Unlock route: participate in all three areas in a meaningful way.
- Evidence: commit history, issue activity, and merged PRs.
- Visible effect: shows broad repo engagement, not just code churn.

#### Coders Lead

- Represents: the highest combined activity among the coders visible in the hub.
- Unlock route: keep contributing enough commits, issues, and PRs to stay at the top of the combined ranking.
- Evidence: Reputation Hub Coders leaderboard.
- Visible effect: highlights broad technical involvement.

### 5. Speaker badges

These badges reward public-facing contribution.

#### First Talk

- Represents: first public speaking contribution in the community flow.
- Unlock route: submit, accept, or complete a talk-related path in Homedir.
- Evidence: event or CFP activity visible in the product.
- Visible effect: recognizes public community presence.

#### Speaker Signal

- Represents: repeated speaking-related activity.
- Unlock route: keep contributing to talks, CFPs, or event speaking paths.
- Evidence: repeated speaker-linked entries.
- Visible effect: shows durable public contribution.

## Badge surfaces

HomeDir can surface these badges in:

- the public profile
- the Reputation Hub
- the community contribution summary
- future badge collections or profile widgets

## Suggested rollout order

1. Define the canonical badge names and icons.
2. Add badge eligibility rules based on existing repository data.
3. Expose badge previews in profile and reputation surfaces.
4. Add tests for unlock logic and visibility.
5. Add only the badges that remain useful after real usage.

## Stability notes

- Keep the catalog small until the unlock rules prove stable.
- Prefer earned badges over decorative labels.
- Avoid duplicating the same badge concept across multiple names.
- Revisit thresholds only when there is evidence that the signals drift.
