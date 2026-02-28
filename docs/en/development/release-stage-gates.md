# Release Stage Gates (Alpha -> Beta -> RC -> GA)

This document defines the minimum gates to move HomeDir across release stages.

## Current Status

- Stage: `ALPHA`
- Baseline CI gate (this iteration):
  - JaCoCo line coverage `>= 60%`
  - JaCoCo branch coverage `>= 40%`
  - `PublicExperienceSmokeTest` mandatory on every PR

These thresholds are intentionally conservative to avoid delivery disruption during the first hardening cycle.

## Stage Gates

## Alpha -> Beta

Required:

1. CI quality gates enabled and stable for at least 2 weeks.
2. `PR Validation` success rate `>= 95%` over rolling 14 days.
3. `Production Release` success rate `>= 95%` over rolling 14 days.
4. Public smoke routes stable (`/`, `/comunidad`, `/eventos`, `/proyectos`) with no critical regressions.
5. Core product loops available (community content, events, identity/profile) without blocking empty states.

## Beta -> Release Candidate (RC)

Required:

1. Feature freeze for major modules (only bugfix, perf, reliability, security).
2. `PR Validation` success rate `>= 97%` over rolling 30 days.
3. `Production Release` success rate `>= 97%` over rolling 30 days.
4. Incident management and rollback drill documented and validated.
5. Security gates active in CI (dependency and static checks).

## RC -> General Availability (GA)

Required:

1. SLO/SLI dashboard active for availability and key endpoint latency.
2. Error budget policy documented and followed.
3. Data governance baseline active (retention, deletion/export paths, auditability).
4. No open P0/P1 defects and no unresolved release blocker.
5. Stable release cadence for at least 30 days.

## Hardening Ratchet Plan (Coverage)

After this PR, increase thresholds in small steps while monitoring pipeline health:

1. Marcha blanca 1: `60/40` (line/branch) for 1 week.
2. Marcha blanca 2: `62/42` if pipeline remains stable.
3. Marcha blanca 3: `65/45`.
4. Beta target candidate: `70/50` (or higher if sustainable).

Do not raise thresholds in the same PR as unrelated feature work.

## Monitoring During Marcha Blanca

Track daily:

- PR Validation pass/fail trend.
- Production Release pass/fail trend.
- Mean time to recover from failed release.
- Critical smoke failures by route.

If failures increase after a gate change:

1. Pause gate ratchet.
2. Open hotfix PR for CI reliability.
3. Resume feature iterations only after stabilization.
