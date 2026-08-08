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

- Reference the issue: `Closes #XXX`
- Use [conventional commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `chore:`, etc.
- Ensure all CI checks pass
- Update documentation if needed
- Add tests for new functionality

## Code Style

- Java: Follow existing project conventions (Quarkus)
- Python: Follow PEP 8
- Documentation: Use Markdown with English as primary, Spanish stubs where applicable

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
1. Add a **type label** (`bug`, `feature-request`, etc.)
2. Add a **priority label** if urgent (`priority:P0`, `priority:P1`)
3. Admins will add **Bounty Hunter labels** if eligible
4. Add `help wanted` if you need assistance

**When creating a PR:**
1. Reference the issue: `Closes #XXX`
2. Add `wip-pr` if still working on it
3. CI and reviewers will add other labels as needed

**Note:** Bounty Hunter points are awarded when:
- Issue is validated by admins (for issue creators)
- PR is merged (for PR authors)

See [GOVERNANCE.md](GOVERNANCE.md) for label management permissions.

## Need Help?

Open a [Discussion](https://github.com/os-santiago/homedir/discussions) or check the [docs](docs/) for guides.
