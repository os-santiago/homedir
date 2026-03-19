# Campaigns Module

`Campaigns` is the internal marketing operations module for HomeDir.

The goal is to turn real product activity into safe, evidence-based social drafts that reflect the
same ADEV discipline used to build the platform itself.

## Scope

- Generate internal campaign drafts from real HomeDir signals.
- Keep drafts reviewable in a private admin surface before any channel is connected.
- Prepare the future rollout for `Discord`, `Bluesky`, `Mastodon`, `LinkedIn`, and optional `X`
  without coupling posting logic to the public user experience.

## Guardrails

1. Rollout is mandatory in three phases:
   - draft-only
   - approval/scheduling
   - channel auto-publish only after production validation
2. Drafts may only use real product data:
   - releases/runtime version
   - insights and delivery activity
   - metrics and business observability
   - events, community, challenges, rewards
3. No fabricated numbers, roadmap claims, or success statements.
4. Channel credentials must live in managed secrets, never in the repo.
5. Every channel integration must include:
   - deduplication
   - per-channel rate limiting
   - global kill switch
   - failure visibility in admin
6. High-risk external channels must stay approval-gated until quality is proven repeatedly.

## Iteration Model

- Iteration 1: internal drafts and hidden admin review
- Iteration 2: controlled approval/scheduling
- Iteration 3: channel publishers and legacy cleanup after production validation

## Data Sources

- `Development Insights`
- `Metrics`
- `Business Observability`
- `Community Picks`
- `Events`
- `Challenges`

## Current Status

- Iteration 1 foundation: enabled
- Iteration 2 controlled approval/scheduling: enabled (internal admin flow only)
- Iteration 3 Discord publisher: implemented behind explicit config, dry-run, dedupe, and rate-limit guardrails
- Bluesky and Mastodon publishers: implemented behind explicit config, dry-run, dedupe, and rate-limit guardrails
- LinkedIn assisted handoff: implemented as manual copy prep + admin confirmation, without direct API publishing
- Campaign admin observability: queue summary, recent activity, and pending LinkedIn handoff visibility enabled
- Cadence guidance: Campaigns now recommends send windows from real HomeDir activity patterns before scheduling drafts
- Public posting: not enabled
- Scheduler-driven external publication: available only when publisher config is enabled
