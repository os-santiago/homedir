# ADEV.md

## Purpose
Unified baseline for ADev execution in Homedir.
This file consolidates repository delivery rules, documentation policy, and local operational notes.

## Scope
Applies to all implementation prompts and iterations for this repository.

## Non-negotiable Rules (Prompt Baseline)
1. One iteration equals one PR with one clear objective.
2. Use atomic commits and Conventional Commits.
3. Never push directly to `main`; always use PR flow.
4. Keep PR scope small; do not mix refactor + feature + styling + infra in one PR.
5. Preserve current look and feel unless explicitly requested.
6. Minimize risk to existing APIs/services and keep backward compatibility in persistence changes.
7. For risky changes, use feature flags or fallback paths.
8. CI required checks must be green before merge.
9. Validate in production after each merge before starting the next iteration.
10. If production fails, stop next iterations, rollback/revert, and open a corrective PR with root cause.
11. Keep changes focused: no unrelated files in PR.
12. Documentation canonical language is English under `docs/en`; `docs/es` is mirror (translation or stub).

## Delivery Workflow
1. Create short-lived branch from `main` with explicit scope (`feat/*`, `fix/*`, `hotfix/*`, `docs/*`, `chore/*`).
2. Implement only agreed iteration scope.
3. Add/update tests and run local validation.
4. Open PR early (Draft allowed), link issue/incident when applicable.
5. Merge with CI green (squash preferred per repository policy).
6. Monitor GitHub Actions `Production Release`.
7. Validate critical production routes with HTTP 200:
   - `/`
   - `/comunidad`
   - `/eventos`
   - `/proyectos`
8. Verify changed behavior manually and check browser console for critical JS/CSS regressions.

## PR and CI Requirements
- PR Quality Suite: `style`, `static`, `arch`, `tests_cov`, `deps`.
- Coverage target: >= 70% lines and branches.
- PR description should include:
  - Summary
  - Why
  - Scope (in/out)
  - Validation
  - Production verification plan
  - Rollback plan

## UI Guardrails
- Reuse existing templates/layout (`layout/main`) and `hd-*` classes.
- No inline CSS when avoidable; extend central stylesheet.
- Keep visual consistency with current product identity.

## Documentation Guardrails
- Allowed docs structure:
  - `docs/README.md`
  - `docs/en/**` (canonical)
  - `docs/es/**` (mirror)
- No markdown docs outside language trees except `docs/README.md`.
- Every doc path in `docs/en` must have corresponding `docs/es` file (translation or stub), and vice versa.
- Update indexes when adding/changing docs:
  - `docs/README.md`
  - `docs/en/README.md`
  - `docs/es/README.md`

## Incident Handling (When Urgent)
- Define incident first (severity P0-P3, impact, timeline, workaround, exit criteria).
- Reference incident in branch and PR (`Refs INC-<id>`).

## Operational Notes (Consolidated from AGENT.md)
- `gh` CLI session is typically authenticated for this repo; verify before PR automation.
- SSH key for VPS is usually available; verify before deployment tasks.
- Keep this baseline updated with new operational reminders when patterns are confirmed.
- Treat previous local helper notes as reference; avoid committing machine-local-only files.
- User recommendations/next steps must be executed, not only proposed.
- Every repo change should go through PR and be tracked until deployment verification.
- For `gh` run polling/waits, use 2-minute intervals up to 15 minutes when applicable.
- Confirm PR reaches deploy stage and the latest built image is active in VPS production.
- Reuse terminal/VPS history to avoid repeated operational mistakes.
- Implementation expectation from user:
  - Execute in iterations with one PR per iteration.
  - Use atomic commits.
  - Avoid impacting look and feel and existing services/APIs.

## Additional Operator Rules (Consolidated from GEMINI.md)
1. Execute commands without waiting for extra permission.
2. When a change fulfills requirements, commit it.
3. Push changes to GitHub periodically.
4. For minor completed changes, create and push a tag.
5. For major completed changes, create and publish a release.
6. For tagging/releasing, bump version references consistently (starting from `pom.xml` where applicable).
7. Follow through until production verification is complete.
8. Validate results in browser after deployment.
9. On merge conflicts, preserve improvements from both sides and avoid losing good changes.
10. Be strict about preserving look and feel, CSS, and HTML structure.
11. Always work on a separate branch before integration.
12. If another agent is changing local files, do not touch the same files; use a dedicated branch per task, sync with origin before coding, run fast local checks before each commit, and integrate only through PR.
13. Reuse and extend Homedir persistence; avoid introducing external services unless explicitly required.

## Canonical References
- `docs/en/CONTRIBUTING.md`
- `docs/en/development/production-safe-delivery-playbook.md`
- `docs/en/development/documentation-language-policy.md`
- `C:\Users\sergi\AGENT.md`
- `C:\Users\sergi\.gemini\GEMINI.md`
