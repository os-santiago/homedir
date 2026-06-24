# PR Reviewer Checklist

Quick reference guide for reviewing pull requests against the [Status Check Matrix](./STATUS_CHECK_MATRIX.md).

## Pre-Review: Verify Required Checks

Before starting code review, verify all required status checks have run and passed.

### Step 1: Identify Change Type

Examine the "Files changed" tab and categorize the PR:

- **Backend/API**: `.java`, `.kt`, `.sql`, `src/main/`, database migrations
- **Frontend/UI**: `.html`, `.css`, `.js`, `templates/`, `static/`
- **Security/Infra**: `.github/workflows/`, `scripts/ci/`, security configs
- **Docs-only**: Only `*.md`, `docs/`, comments
- **Release/Deploy**: `version` files, release workflows, deployment configs

### Step 2: Check Universal Status Checks (All PRs)

All PRs must have these 6 checks passing:

- [ ] PR Quality — Suite / style
- [ ] PR Quality — Suite / static
- [ ] PR Quality — Suite / arch
- [ ] PR Quality — Suite / tests_cov
- [ ] PR Quality — Suite / deps
- [ ] PR CI (Build, Native, SBOM/Scan) / sbom

### Step 3: Check Change-Specific Requirements

#### Backend/API Changes
- [ ] Build & Verify
- [ ] SAST - CodeQL Analysis (java-kotlin)
- [ ] Load Test (High-Impact Services)
- [ ] Secret Scanning
- [ ] CodeQL Java (Advisory) - advisory only

#### Frontend/UI Changes
- [ ] Build & Verify
- [ ] CodeQL

#### Security/Infrastructure Changes
- [ ] Dependency Review
- [ ] SBOM & Security Scan
- [ ] grype
- [ ] Secret Scanning
- [ ] Dependency Review (Advisory) - advisory only

#### Release/Deploy Changes
- [ ] Build & Verify
- [ ] SBOM & Security Scan
- [ ] Dependency Review
- [ ] Quality Gate Summary

#### Docs-Only Changes
- [ ] Universal checks only (no additional requirements)

## Code Review Checklist

After verifying status checks, review the code:

### General
- [ ] PR title follows Conventional Commits format
- [ ] PR description explains **why** the change is needed
- [ ] All commits are signed-off (`Signed-off-by:` in commit message)
- [ ] No merge commits (rebase-only workflow)
- [ ] Closes appropriate issue(s) with `Closes #NNN` in description

### Code Quality
- [ ] Code follows project style conventions
- [ ] No commented-out code (unless explicitly documented)
- [ ] No TODO/FIXME without associated issue
- [ ] Error handling is appropriate and not swallowed
- [ ] Logging added for key operations (debug, info, error levels)

### Testing
- [ ] Tests added for new functionality
- [ ] Tests updated for changed functionality
- [ ] Edge cases covered (null, empty, boundary conditions)
- [ ] Integration tests added if behavior crosses module boundaries

### Security
- [ ] No hardcoded credentials, tokens, or secrets
- [ ] User input is validated and sanitized
- [ ] SQL queries use parameterization (no string concatenation)
- [ ] No new dependencies without justification
- [ ] Dependency versions pinned (no `latest` or floating versions)

### Documentation
- [ ] Public APIs documented (JavaDoc, TSDoc, etc.)
- [ ] README updated if usage changes
- [ ] Migration guide added if breaking change
- [ ] Changelog entry added (if project uses one)

### Scope
- [ ] Changes are focused on single issue (atomic PR)
- [ ] No unrelated refactoring or style changes
- [ ] No commented-out debugging code

## Advisory Check Failures

If advisory (non-blocking) checks fail, evaluate:

1. **CodeQL Java (Advisory)**: Review findings, create follow-up issue if real
2. **Dependency Review (Advisory)**: Check for high-severity CVEs, assess risk

Do NOT approve if advisory failures indicate real security issues, even if non-blocking.

## When to Reject

Reject the PR (request changes) if:

- Any **required blocking check** failed or did not run
- Code introduces **security vulnerabilities** (even if checks pass)
- Changes are **out of scope** for the linked issue
- **Tests are missing** for new functionality
- PR **mixes multiple unrelated changes** (violates atomic commit rule)
- Commits **not signed-off** (DCO requirement)
- Commit messages **don't follow Conventional Commits**

## When to Request Bypass

Bypass is **only** for emergencies:

- Production outage requiring immediate fix
- Security patch for active exploit
- Broken main branch blocking all development

**Process**:
1. Document emergency in PR description
2. Tag maintainer for bypass approval
3. Create follow-up issue to satisfy skipped checks
4. Post-merge: run skipped checks and verify no regressions

## Exception Approval

If PR cannot meet all criteria (non-emergency):

1. Author must document **which criteria not met** and **why**
2. Author must create **follow-up issue(s)** for deferred work
3. Reviewer evaluates **risk vs benefit**
4. Maintainer provides **explicit written approval** in review comment

Do NOT approve exceptions without maintainer sign-off.

## Useful Commands

Check PR status from CLI:
```bash
gh pr view <PR-number> --json statusCheckRollup
```

List failed checks:
```bash
gh pr checks <PR-number> --fail
```

View PR diff:
```bash
gh pr diff <PR-number>
```

## Quick Decision Tree

```
Is this an emergency production fix?
├─ YES → Tag maintainer for bypass approval
└─ NO → Continue review

Are all universal checks passing?
├─ NO → Request author to fix failing checks
└─ YES → Continue

Are all change-type-specific checks passing?
├─ NO → Verify required checks ran; request fixes
└─ YES → Continue

Is code quality acceptable?
├─ NO → Request changes with specific feedback
└─ YES → Continue

Are tests comprehensive?
├─ NO → Request additional test coverage
└─ YES → APPROVE (with optional comments)
```

## Related Documents

- [Status Check Matrix](./STATUS_CHECK_MATRIX.md) - Full check requirements by change type
- [Definition of Ready/Done](./DEFINITION_OF_READY_DONE.md) - Issue/PR completion criteria
- [Emergency Break-Glass Runbook](#) - Emergency bypass procedures

---

**Maintained by**: Platform Engineering
**Last updated**: 2026-06-24
