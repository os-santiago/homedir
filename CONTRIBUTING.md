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
- **MUST NOT** apply `scc-*` labels manually — those are managed by automation.
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
- `wip-pr` - Someone is working on this (has a PR or draft PR)
- `needs-human` - Requires human decision or intervention

**Resolution Labels:**
- `duplicate` - Already exists
- `invalid` / `no valido` - Doesn't seem right
- `wontfix` / `no solucionar` - Won't be worked on

### Pull Request Labels

**Workflow Status:**
- `wip-pr` - Work in progress
- `ready-to-implement` - Ready for review/merge

**Automated SDLC Labels (managed by automation):**
- `scc-queued` - In AI SDLC queue
- `scc-running` - AI worker claimed this
- `scc-pr-open` - AI worker opened PR
- `scc-waiting-checks` - Waiting for CI checks
- `scc-under-review` - Under automated review
- `scc-approved` - Passed checks and review
- `scc-merged` - Successfully merged
- `scc-failed` - Failed, needs inspection
- `scc-failing-checks` - Has failing CI checks
- `scc-coverage-gap` - Lacks issue coverage

### How to Use Labels

**When creating an issue:**
1. **MUST** add a **type label** (`bug`, `enhancement`, `documentation`, `feature-request`, `platform-maintenance`)
2. **MUST** add a **priority label** (`priority:P0`–`priority:P3`). If unsure, use `priority:P2`.
3. Admins will add **Bounty Hunter labels** if eligible
4. Add `help wanted` if you need assistance
5. Add `needs-human` if a human decision is required

**When creating a PR:**
1. **MUST** reference the issue: `Closes #XXX` or `Fixes #XXX`
2. Add `wip-pr` if still working on it (draft PR)
3. CI will auto-assign PR state labels (`scc-waiting-checks`, `scc-failing-checks`, `scc-approved`)
4. **MUST NOT** apply `scc-*` labels manually

**Note:** Bounty Hunter points are awarded when:
- Issue is validated by admins (for issue creators)
- PR is merged (for PR authors)

See [GOVERNANCE.md](GOVERNANCE.md) for label management permissions.

## Interacting with Autonomous AI

This repository uses autonomous AI workers (SCC/SDLC) that can claim issues and open PRs. Here's how human contributors interact with them:

- If an issue has the `scc-running` label, an AI worker is actively working on it — don't start work without checking
- If an issue has the `needs-human` label, it requires human decision — AI agents should not proceed
- If a PR has the `scc-under-review` label, the AI worker is addressing feedback — don't force changes
- Human reviewers can override any `scc-*` label by commenting "human override: <reason>"
- The `scc-*` label lifecycle: `scc-queued` → `scc-running` → `scc-pr-open` → `scc-waiting-checks` → `scc-approved` → `scc-merged`

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
