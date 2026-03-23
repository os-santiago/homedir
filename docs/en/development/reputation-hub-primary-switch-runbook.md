# Reputation Hub Primary Switch Runbook

This runbook defines the safe production procedure to promote `Reputation Hub` as the primary community experience while keeping a fast rollback path.

## Scope

Use this runbook when `reputation.hub.primary.enabled` is about to change in production.

## Required Inputs

- merged PR and commit hash for the rollout
- green checks for `PR Validation` and security advisory workflows
- operator with access to production env vars and service restart
- access to an admin account (`admin` or `admin-view`) for backoffice diagnostics

## Flag Contract

Primary replacement is active only when all conditions are true:

- `reputation.engine.enabled=true`
- `reputation.hub.ui.enabled=true`
- `reputation.hub.primary.enabled=true`
- user is admin, or `reputation.hub.nav.public.enabled=true`

If any condition is false, `Community Board` remains the fallback.

## Pre-Switch Checklist

1. Confirm current production baseline:
   - `/` returns `200`
   - `/comunidad` returns `200`
   - `/eventos` returns `200`
   - `/proyectos` returns `200`
2. Confirm Hub routes are healthy for the target audience:
   - `/comunidad/reputation-hub`
   - `/comunidad/reputation-hub/how`
3. Confirm English aliases still redirect:
   - `/community/reputation-hub` -> `/comunidad/reputation-hub`
   - `/community/reputation-hub/how` -> `/comunidad/reputation-hub/how`
4. Confirm admin diagnostics endpoint is reachable (when shadow read is enabled):
   - `GET /api/private/admin/reputation/phase2/diagnostics`
5. Capture screenshot or terminal evidence for every check.

## Promotion Procedure

1. Update production env (`/etc/homedir.env`):
   - set `reputation.hub.primary.enabled=true`
2. Keep other rollout flags unchanged unless explicitly part of the change request.
3. Restart the service with the standard production restart method.
4. Re-run the pre-switch checklist and compare results.

## Post-Switch Validation

## Smoke

- `/comunidad/board` and `/comunidad/board/{group}` redirect to `/comunidad/reputation-hub` for eligible audience.
- Community submenu does not show a conflicting `Community Board` link when replacement gate is active.
- Hub pages render leaderboards and recognized contributions without template errors.
- Prefer automated smoke command for repeatability:
  - `scripts/reputation-hub-smoke.sh https://homedir.opensourcesantiago.io primary-on`

## Baseline Smoke Automation (Before Switch)

When rollout flags are still OFF, run:

- `scripts/reputation-hub-smoke.sh https://homedir.opensourcesantiago.io baseline-off`

## Leaderboard Sanity

- Weekly, monthly, and rising sections render in the Hub UI.
- No obvious duplicate identities in top rows (same user repeated with conflicting rank).
- Diagnostics (`phase2/diagnostics`) show expected event growth and no error payload.

## Recognition Anti-Abuse Sanity

Use `POST /api/community/reputation/recognitions` with authenticated users:

- self-recognition attempt returns `400` with `recognition_self_not_allowed`
- immediate repeated recognition returns `429` (`recognition_cooldown_active` or `recognition_already_recorded`)
- invalid payload returns `400` with `recognition_invalid_payload`

Run high-volume daily-limit verification in staging, not in production.

## Rollback Procedure

1. Set `reputation.hub.primary.enabled=false`.
2. Restart service.
3. Verify:
   - `/comunidad/board` returns board page again (no forced redirect)
   - community submenu shows `Community Board` entry
   - core public smoke routes remain `200`
4. Record rollback reason and timestamp in shared session handoff.

## Evidence To Store In Shared Workspace

- PR number, merge commit, and checks URLs
- exact timestamp of flag change
- smoke status table before and after switch
- diagnostics sample payload (`phase2/diagnostics`)
- decision on keep-switch-on or rollback
