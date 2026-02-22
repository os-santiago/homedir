# Production-Safe Delivery Playbook (2-Week Proven Pattern)

This guide captures the delivery pattern used in the last two weeks to ship large changes with low production incidents.

## Goal

Ship fast without breaking production by standardizing:

- Small, focused changes
- Strong CI gates
- Fast production verification
- Fast rollback path

## Operating Principles

1. One iteration, one PR, one clear objective.
2. Atomic commits only (easy to review, revert, and audit).
3. Preserve existing look and feel unless explicitly requested.
4. Minimize risk on existing APIs/services.
5. Validate in production after each merge before starting the next iteration.

## Standard Iteration Flow

1. Create branch with explicit scope.
2. Implement only the agreed change for that iteration.
3. Add/update tests for the changed behavior.
4. Run local validation.
5. Open PR with clear summary and validation evidence.
6. Merge with CI green (auto-merge allowed only with required checks).
7. Monitor GitHub Actions `Production Release`.
8. Validate production endpoints and critical UI paths.
9. Move to next iteration only if validation is successful.

## Mandatory Validation Checklist

Before merge:

- `PR Validation` workflow is green.
- Changed pages render correctly in local test/smoke.
- No unrelated file changes in PR.

After deploy:

- `Production Release` workflow is green.
- Main routes return HTTP 200:
  - `/`
  - `/comunidad`
  - `/eventos`
  - `/proyectos`
- Changed route behavior is manually verified.
- Browser console has no new critical errors on changed pages.

## Risk Guardrails

- Keep PR size small; split big initiatives into incremental PRs.
- Never bundle refactor + feature + styling + infra in one PR.
- For high-risk changes, add a temporary feature flag or fallback path.
- Avoid expensive runtime calls per request; prefer cache + scheduled refresh.
- Keep persistence changes backward compatible when possible.

## Production Rollback Pattern

If production validation fails:

1. Stop new feature iterations.
2. Revert the last PR or deploy previous stable release tag.
3. Confirm health checks and critical routes.
4. Open a fix PR with explicit root cause and prevention note.

## PR Template (Recommended)

Use this structure in every PR:

- Summary
- Why
- Scope (what is included / excluded)
- Validation (tests and manual checks)
- Production verification plan
- Rollback plan

## Metrics to Track Weekly

- PR validation success rate
- Production release success rate
- Mean time from merge to production validation
- Number of rollbacks/reverts
- Open production bugs older than 7 days

Targets should be defined and adjusted by the team based on current stability goals.

