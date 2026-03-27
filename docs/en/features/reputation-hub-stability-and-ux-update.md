# Reputation Hub Stability and UX Update

## Status (as of 2026-03-25)

The Reputation Hub rollout is in a stable pre-GA state with phase gates 0-5 implemented and covered by focused tests.

Measured validation on HEAD:

- Command:
  - `quarkus-app\mvnw.cmd "-Dtest=ReputationEngineFeatureFlagTest,ReputationEngineServiceTest,ReputationEventTaxonomyTest,AdminReputationApiResourceTest,AdminReputationPhase2ApiResourceTest,PublicProfileReputationSummaryTest,ReputationHubMigrationBannerTest,ReputationHubResourceTest,ReputationRecognitionApiDisabledTest,ReputationRecognitionApiResourceTest,CommunityReputationNavExposureTest,CommunityBoardPrimarySwitchTest,ReputationHubServicePerformanceTest" test`
- Result:
  - 31 tests run
  - 0 failures
  - build success

## What is already improved

## User experience

- Navigation migration is now progressive and reversible through flags:
  - `reputation.hub.ui.enabled`
  - `reputation.hub.nav.public.enabled`
  - `reputation.hub.primary.enabled`
- Community submenu hides legacy board link when replacement gate is active, reducing user confusion.
- `/community/reputation-hub` aliases correctly to `/comunidad/reputation-hub`.
- A migration onboarding banner is available when primary switch is active.

## Public profile

- Public profile includes reputation summary (phase 3 surface).
- Exposure is controlled by rollout flags to protect stability.
- i18n coverage is validated in reputation-related public routes.

## Virtuous loop (activity -> feedback -> incentives)

- Reputation engine dimensions are active:
  - Participation
  - Contribution
  - Recognition
  - Consistency
- Reputation Hub includes:
  - Weekly and monthly leaderboards
  - Rising members
  - Recognized contributions
- Recognition API has baseline anti-abuse protections:
  - no self-recognition
  - cooldown and duplicate controls
  - daily quota controls
- GA readiness now verifies recognition diversity in-window (minimum unique validators), not just raw recognition volume.
- GA readiness now also enforces a max validator-concentration threshold for in-window recognitions.
- GA readiness now also requires minimum diversity of recognized targets in-window.
- GA readiness now also requires minimum diversity of recognized source objects in-window.

## Remaining work to call GA stable

1. Collect production-like p95/p99 server latency for:
   - `/comunidad/reputation-hub`
   - `/comunidad/reputation-hub/how`
2. Collect web-vitals in production-like conditions:
   - LCP
   - INP
   - TBT
3. Hold primary switch without rollback for at least one weekly and one monthly leaderboard cycle.
4. Keep regression guardrails green (payload budgets and targeted tests) for two release windows.

## Known uncertainty

- Functional and guardrail tests are green, but no fresh production-like browser traces were captured in this update.
- Next decision-quality evidence should come from p95/p99 + web-vitals measurements.

## Share-ready summary (internal/external)

HomeDir advanced the Reputation Hub to a stable pre-GA stage with progressive rollout and fast rollback controls. The new experience improves navigation clarity, adds public reputation context on profiles, and closes the activity-feedback loop through leaderboards and recognized contributions with anti-abuse protections. The final GA gate is now measurement-driven: keep switch stability across weekly/monthly cycles and confirm p95/p99 plus web-vitals under production-like load.
