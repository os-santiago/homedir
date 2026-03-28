# Reputation Hub Roadmap (Stability-First)

## Status

- Document: draft v1 (implementation plan derived from PRD)
- Product owner: HomeDir
- Scope type: strategic replacement of `Community Board` with gradual rollout

## Objective

Replace `Community Board` with `Reputation Hub` without destabilizing HomeDir runtime, admin flows, or user trust.

`Reputation Hub` will convert meaningful participation into visible reputation through:

- `Reputation Engine`
- public profile reputation summary
- contextual leaderboards (`weekly`, `monthly`, `rising`)
- lightweight recognition layer

## Stability Constraints

This roadmap is constrained by current delivery rules:

- one atomic PR per iteration by default
- mandatory CI green before merge
- production verification before advancing to next iteration
- shared handoff context updated at each checkpoint
- backward compatibility for routes and templates when replacements are introduced

## MVP Scope (Committed)

- rename and replace `Community Board` user-facing concept with `Reputation Hub`
- event-driven reputation model with 4 dimensions:
  - `Participation`
  - `Contribution`
  - `Recognition`
  - `Consistency`
- profile reputation summary with reputation state and top strengths
- leaderboards:
  - top contributors this week
  - top contributors this month
  - rising members
- recognized contributions block
- `How reputation works` page
- minimum anti-abuse protections

## Out of Scope (MVP)

- external reputation integrations (`GitHub`, `LinkedIn`, others)
- multi-tenant cross-community reputation
- advanced endorsement graphs
- monetization and reward economy
- ML-based scoring and recommendations

## Delivery Model

Use a staged rollout with hard gates and rollback checkpoints. No stage moves forward without previous production verification.

## Phase 0 - Foundation Audit and Instrumentation (No UX change)

## Goal

Prepare safe migration by measuring current behavior and creating observability baseline.

## Deliverables

- inventory of existing `Community Board` data sources and routes
- event taxonomy mapping from existing actions to candidate reputation events
- metrics baseline dashboard:
  - weekly board visits
  - profile visits
  - repeat usage
- feature flags added but disabled:
  - `reputation.engine.enabled`
  - `reputation.hub.ui.enabled`
  - `reputation.recognition.enabled`

## Exit gate

- no user-visible regressions
- baseline metrics available for comparison
- compile + targeted tests green

## Rollback

- disable all `reputation.*` flags (already default off)

## Phase 1 - Hidden Reputation Engine v1 (Write path only)

## Goal

Start generating internal reputation events and aggregates without user exposure.

## Deliverables

- reputation event schema and persistence using existing persistence layer
- ingestion from existing actions:
  - `quest_completed`
  - `event_attended`
  - `event_speaker`
  - `content_published`
- aggregation job:
  - total internal score
  - per-dimension score
  - weekly and monthly windows
  - rising delta window
- idempotency + duplicate-event protection

## Exit gate

- event ingestion and aggregation pass targeted tests
- no production performance regression on hot paths
- data write amplification within acceptable limits

## Rollback

- disable `reputation.engine.enabled`
- keep stored events for forensic analysis

## Phase 2 - Shadow Mode and Explainability API (Read path internal)

## Goal

Validate scoring quality and explainability before public rollout.

## Deliverables

- internal-only API/resource for:
  - user reputation summary
  - dimension breakdown
  - top recognized contributions candidates
- admin/internal diagnostics view (not public)
- score explainability payload:
  - latest meaningful events
  - reason labels (no formula leakage)
- anti-abuse minimum controls:
  - duplicate detection
  - cooldown for repetitive low-value events

## Exit gate

- internal stakeholders can explain score changes for sample users
- shadow outputs are coherent against manual QA samples
- no PII leakage in logs and no risky raw path logging

## Rollback

- disable internal read feature flag
- keep engine in write-only mode

## Phase 3 - Reputation Summary on Public Profile (Limited exposure)

## Goal

Expose profile-first value before replacing board navigation.

## Deliverables

- public profile card:
  - reputation state (`Emerging`, `Active`, `Recognized`, `Trusted`, `Impactful`)
  - top strengths
  - badges preview
  - recent milestone
- no leaderboard replacement yet
- locale-ready copy in `i18n` bundles (`en` + `es`)

## Exit gate

- profile renders correctly across desktop/mobile and locales
- user can understand "why this reputation" from provided cues
- no elevated support incidents on profile confusion

## Rollback

- disable profile reputation widget flag
- fallback to previous profile sections only

## Phase 4 - Reputation Hub Beta (Parallel to Community Board)

## Goal

Run `Reputation Hub` in production while keeping current board fallback.

## Deliverables

- new `Reputation Hub` route with:
  - hero summary
  - weekly leaderboard
  - monthly leaderboard
  - rising members
  - recognized contributions
  - how-it-works section
- nav entry canary for a limited audience (for example admins + small percentage)
- recognition actions MVP:
  - `recommended`
  - `helpful`
  - `standout`
- anti-abuse for recognition:
  - no self-recognition
  - per-user daily cap
  - cooldown + anomaly logs

## Exit gate

- guardrail metrics healthy:
  - no abnormal concentration spike
  - no anomalous recognition bursts
  - no significant error-rate increase
- positive comprehension feedback from beta users

## Rollback

- route hidden by flag
- existing `Community Board` remains primary path

## Phase 5 - Primary Navigation Switch (Controlled replacement)

## Goal

Make `Reputation Hub` the primary replacement while preserving emergency fallback.

## Deliverables

- replace `Community Board` nav entry with `Reputation Hub`
- deprecate board route with compatibility redirect
- onboarding banner/message to explain change
  - production verification handled by the SDLC delivery playbook, not by an in-product admin module

## Exit gate

- replacement stable for one full cycle (weekly + monthly turnover)
- north-star and supporting metrics are at least neutral vs baseline
- no critical regressions in profile and community pages

## Rollback

- revert nav to `Community Board`
- disable hub exposure flag
- keep ingestion on for data continuity

## Phase 6 - Hardening and Legacy Cleanup

## Goal

Remove legacy board implementation after sustained stability.

## Deliverables

- remove deprecated board templates/resources
- finalize API contracts and tests for reputation module
- document stable operating model and anti-abuse playbook

## Exit gate

- all reputation and profile regression tests green
- no rollback triggers across at least two release windows
- docs and runbooks fully updated

## Rollback

- restore compatibility branch if late issue appears before full cleanup merge

## Technical Plan (MVP)

## Data model

Minimum event schema:

- `event_id`
- `actor_user_id`
- `event_type`
- `event_category`
- `weight_base`
- `source_object_type`
- `source_object_id`
- `created_at`
- `validated_by_user_id` (nullable)
- `validation_type` (nullable)
- `scope_type` (nullable)
- `scope_id` (nullable)

## Aggregation outputs

- per-user total internal score
- per-dimension score
- weekly leaderboard score
- monthly leaderboard score
- rising delta score
- badge eligibility state

## Scoring principles (MVP)

- meaningful actions weigh more than volume
- recognition weighs more than raw activity
- repeated trivial activity has decreasing marginal value
- consistency bonus is bounded
- public output is simplified; internal formula remains adjustable

## Safety Gates Per PR

Every iteration should include:

- focused compile/test validation for touched areas
- UI assertions for changed templates/routes/i18n copy
- CodeQL-minded preflight for:
  - redirects
  - auth/session boundaries
  - log safety with user-derived fields
  - persistence path and payload handling

## Initial KPI and Guardrail Targets

Targets should be validated against baseline during beta:

- north star: increase meaningful reputation actions per active user per month
- comprehension proxy: profile reputation section engagement without elevated bounce
- fairness guardrail: top-10 concentration does not exceed agreed threshold
- abuse guardrail: suspicious recognition patterns remain below agreed threshold

## Open Decisions to Resolve Before Phase 4

- show reputation states from day one or after minimum event history
- allow recognition for non-content objects in MVP (quests/help/events)
- expose exact point deltas or only milestone narratives
- define threshold policy for leaderboard concentration alerts

## Recommended First Iteration Sequence

1. `docs` + feature flag skeleton + event taxonomy mapping (no behavior change)
2. engine ingestion for existing actions with hidden aggregates
3. profile summary widget behind flag
4. hub beta route + limited audience exposure
5. nav switch + board deprecation redirect

This sequence keeps platform stability ahead of feature velocity and ensures each stage is reversible.
