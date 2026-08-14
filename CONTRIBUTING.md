# Contributing to Homedir

## Security: Pre-commit Secret Scanning

Before contributing, install the pre-commit secret scanning hook:

```bash
# 1. Install gitleaks
# macOS: brew install gitleaks
# Windows: scoop install gitleaks
# Linux: Download from GitHub releases

# 2. Install the hook
cp hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# 3. Verify (should block)
echo "aws_key=AKIAIOSFODNN7EXAMPLE" > test.txt
git add test.txt && git commit -m "test" || echo "Working!"
git reset HEAD test.txt && rm test.txt
```

See [docs/guides/pre-commit-secret-scanning.md](docs/guides/pre-commit-secret-scanning.md) for details.

---

## Contributor License Agreement

By contributing to Homedir, you agree to the terms of the [Contributor License Agreement](CLA.md). Each commit must include a `Signed-off-by` trailer:

```bash
git commit -s -m "feat: my contribution"
```

This certifies that you have the right to submit the contribution under the Apache 2.0 license and that you have read and agree to the CLA.

## Getting Started

1. Fork the repository
2. Clone your fork
3. Create a feature branch: `git checkout -b feat/issue-XXX-description`
4. Make your changes
5. Commit with `git commit -s` (signed-off-by)
6. Push and create a Pull Request

## Pull Request Guidelines

- **MUST** reference the issue: `Closes #XXX` or `Fixes #XXX` — no exceptions. If there's no issue, create one first.
- **MUST** use [conventional commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `chore:`, etc.
- **MUST** write PR title, body, and commits in **English**.
- **MUST** use branch naming: `feat/issue-XXX-description`, `fix/issue-XXX-description`, `docs/issue-XXX-description`.
- **MUST** include `Signed-off-by` in commits (`git commit -s`).
- **MUST** add exactly one `pr:risk-*` label (`pr:risk-low`, `pr:risk-medium`, `pr:risk-high`, `pr:risk-critical`) based on change type
- **MUST NOT** apply `pr:` state labels (`pr:draft`, `pr:needs-review`, `pr:approved`, etc.) — those are managed by automation
- Ensure all CI checks pass before merge
- Update documentation if needed
- Add tests for new functionality
- Use the PR template (`.github/PULL_REQUEST_TEMPLATE.md`) when available

## Code Style

- Java: Follow existing project conventions (Quarkus)
- Python: Follow PEP 8
- Documentation: Use Markdown. Both English and Spanish are acceptable.

## Language Policy

| Element | Rule |
|---------|------|
| **PRs** (title, body, commits, branch names) | English **mandatory** — no exceptions |
| **Issues** | English **default** (EN templates). Spanish **optional** via `es/` templates |
| **Documentation** | Both English and Spanish are acceptable |
| **Internal rules/governance** | Both English and Spanish are acceptable |
| **Labels** | English only (no legacy ES labels) |

**Deprecated labels:** The following Spanish labels are deprecated and will be auto-migrated to their English equivalents by CI:

| Legacy (ES) | Canonical (EN) |
|-------------|----------------|
| `error` | `bug` |
| `mejora` | `enhancement` |
| `buen primer issue` | `good first issue` |
| `no valido` | `invalid` |
| `no solucionar` | `wontfix` |
| `pregunta` | `question` |
| `Se necesita ayuda` | `help wanted` |

Do not apply legacy ES labels to new issues or PRs.

## Labels Guide

Labels help organize issues and PRs, track their status, and identify work eligible for Bounty Hunter points.

### Issue Labels

**Type Labels:**
- `bug` - Something isn't working
- `enhancement` / `feature-request` - New feature or enhancement requests
- `documentation` - Documentation improvements
- `question` / `pregunta` - Questions about the project

**Bounty Hunter Eligible (see [Bounty Hunters Program](README.md#-bounty-hunters-program)):**
- `bug-impact-low` - Low impact bug (5 pts)
- `bug-impact-medium` - Medium impact bug (15 pts)
- `bug-impact-high` - High impact bug (30 pts)
- `feature-request` - New feature (20 pts)
- `documentation-improvement` - Documentation work (10 pts)
- `platform-maintenance` - Platform maintenance (15 pts)

**Priority Labels:**
- `priority:P0` - Critical, immediate attention
- `priority:P1` - High priority
- `priority:P2` - Medium priority
- `priority:P3` - Low priority

**Status Labels:**
- `good first issue` / `buen primer issue` - Good for newcomers
- `help wanted` / `Se necesita ayuda` - Extra attention needed
- `needs-human` - Requires human decision or intervention

**Resolution Labels:**
- `duplicate` - Already exists
- `invalid` / `no valido` - Doesn't seem right
- `wontfix` / `no solucionar` - Won't be worked on

### Pull Request Labels

**PR State Labels** (managed by automation — do NOT apply manually):
- `pr:draft` - PR is draft / work in progress
- `pr:checks-pending` - CI checks are running
- `pr:checks-failed` - CI checks are failing
- `pr:needs-review` - CI green, ready for maintainer review
- `pr:changes-requested` - Maintainer requested changes
- `pr:approved` - Required human approvals met per risk level
- `pr:merged` - PR has been merged
- `pr:blocked` - Blocked: merge conflicts or other blocker

**PR Risk Labels** (applied by contributor, exactly one required):
- `pr:risk-low` - Docs, typos, config (min 1 approval)
- `pr:risk-medium` - Features, refactors, new deps (min 2 approvals, 1 code owner)
- `pr:risk-high` - Security, breaking changes (min 2 approvals, code owners + security)
- `pr:risk-critical` - Auth, encryption, financial (min 3 approvals, code owners + security)

**PR Self-Attestation Labels** (applied by contributor, auto-validated by CI):
- `pr:traceability-ok` - `Closes #N` present, issue exists with priority + type labels
- `pr:acceptance-ok` - All acceptance criteria from linked issue are met
- `pr:tests-ok` - Tests added/updated for new functionality
- `pr:i18n-ok` - i18n complete (EN + ES) for user-facing changes

### How to Use Labels

**When creating an issue:**
1. **MUST** add a **type label** (`bug`, `enhancement`, `documentation`, `feature-request`, `platform-maintenance`)
2. **MUST** add a **priority label** (`priority:P0`–`priority:P3`). If unsure, use `priority:P2`.
3. Admins will add **Bounty Hunter labels** if eligible
4. Add `help wanted` if you need assistance
5. Add `needs-human` if a human decision is required

**When creating a PR:**
1. **MUST** reference the issue: `Closes #XXX` or `Fixes #XXX`
2. **MUST** add exactly one `pr:risk-*` label based on change type
3. **SHOULD** add self-attestation labels (`pr:traceability-ok`, `pr:tests-ok`, `pr:i18n-ok`, `pr:acceptance-ok`) when applicable
4. CI will auto-assign PR state labels (`pr:checks-pending`, `pr:needs-review`, `pr:approved`, etc.)
5. **MUST NOT** apply `pr:` state labels manually — those are managed by automation

**Note:** Bounty Hunter points are awarded when:
- Issue is validated by admins (for issue creators)
- PR is merged (for PR authors)

See [GOVERNANCE.md](GOVERNANCE.md) for label management permissions.

## Interacting with AI-Assisted PRs

This repository uses AI-assisted workflows where contributors' AI tools pre-verify and tag PRs. Here's how to interact:

- If a PR has `pr:draft`, the contributor is still working — don't review yet
- If a PR has `pr:needs-review`, CI is green and it's ready for maintainer review
- If a PR has `pr:changes-requested`, a maintainer has requested changes — wait for the contributor to address them
- If a PR has `pr:approved`, the required human approvals have been met based on the `pr:risk-*` label
- Self-attestation labels (`pr:traceability-ok`, `pr:tests-ok`, `pr:i18n-ok`, `pr:acceptance-ok`) indicate the contributor's AI has pre-verified these aspects — the `pr-readiness-validator.yml` workflow auto-validates and removes false claims
- If an issue has the `needs-human` label, it requires human decision — AI agents should not proceed
- The PR label lifecycle: `pr:draft` → `pr:checks-pending` → `pr:needs-review` → `pr:approved` → `pr:merged`

## Governance Documents

The repository includes governance documents in `config/docs/governance/`. Contributors should be aware of:

- [Definition of Ready/Done](config/docs/governance/DEFINITION_OF_READY_DONE.md) — Criteria for issues to be ready for work and complete
- [Triage Runbook](config/docs/governance/TRIAGE_RUNBOOK.md) — Issue triage process
- [PR Review Policy](config/docs/governance/PR_REVIEW_POLICY.md) — Approval requirements by risk level
- [Severity/Priority Contract](config/docs/governance/SEVERITY_PRIORITY_CONTRACT.md) — Severity × Priority matrix
- [Label Taxonomy](config/docs/governance/LABEL_TAXONOMY.md) — Canonical label catalog
- [Reviewer Checklist](config/docs/governance/REVIEWER_CHECKLIST.md) — Review checklist by change type
- [AGENTS.md](AGENTS.md) — Full guide for AI agents working on this repo

## Need Help?

Open a [Discussion](https://github.com/os-santiago/homedir/discussions) or check the [docs](docs/) for guides.
