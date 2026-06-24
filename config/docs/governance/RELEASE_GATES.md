# Release Gates: PR Validation → Quality Gates → Production Release

**Document Status**: Draft  
**Last Updated**: 2026-06-24  
**Owner**: Engineering Leadership  
**Relates To**: Issue #851

## Purpose

This document defines the formal contract for quality gates from Pull Request through Merge to Production Release on the `main` branch. It ensures consistent blocking criteria across all stages of code delivery and prevents merges with unmitigated risk.

## Overview

The release gate chain consists of three stages:

```
┌─────────────┐     ┌──────────────┐     ┌────────────────────┐
│             │     │              │     │                    │
│ PR → main   │────▶│ main branch  │────▶│ Production Release │
│ Validation  │     │ Quality      │     │ Deployment         │
│             │     │ Gates        │     │                    │
└─────────────┘     └──────────────┘     └────────────────────┘
```

Each stage must pass **all hard blocks** before proceeding. Warnings are advisory only but should be addressed within next iteration.

---

## Stage 1: PR Validation (Pre-Merge)

**Workflow**: `.github/workflows/pr-validation.yml`  
**Trigger**: Pull request opened/updated against `main`  
**Purpose**: Catch issues before they enter the main branch

### Hard Blocks (Must Pass)

| Check | Type | Workflow Job | Bypass Allowed |
|-------|------|--------------|----------------|
| **Conventional Commits** | Lint | `commitlint` | ❌ Never |
| **Linting (Ruff)** | Code Quality | `ruff-check` | ❌ Never |
| **Type Checking (Mypy)** | Static Analysis | `mypy-check` | ⚠️ Exception only[^1] |
| **Unit Tests** | Functional | `pytest` | ❌ Never |
| **Security Scan (Gitleaks)** | Security | `gitleaks-scan` | ❌ Never |
| **License Compliance** | Legal | `license-check` | ⚠️ Exception only[^2] |
| **PR Template Completion** | Process | Manual verification | ⚠️ Maintainer discretion |

[^1]: Mypy bypass: Only for external type stub issues documented in PR. Requires maintainer approval + tracking issue.
[^2]: License bypass: Only for newly added dependencies under review. Must have tracking issue and resolution deadline.

### Warnings (Advisory)

| Check | Purpose | Resolution Requirement |
|-------|---------|----------------------|
| **Code Coverage** | Quality tracking | Document in PR if decreases >2% |
| **Dependency Updates** | Security hygiene | Address in follow-up PR if flagged |
| **Documentation Changes** | Completeness | Update before merge if affecting public API |

### Branch Protection Requirements

**Required for merge to `main`:**
- ✅ At least 1 approving review from maintainer
- ✅ All PR Validation checks passing
- ✅ Conversation resolution required
- ✅ No force-push allowed after approval
- ✅ Branch must be up-to-date with base

---

## Stage 2: Main Branch Quality Gates (Post-Merge)

**Workflow**: `.github/workflows/quality-gates.yml`  
**Trigger**: Push to `main` branch  
**Purpose**: Continuous verification that `main` remains releasable

### Hard Blocks (Must Pass)

| Check | Type | Workflow Job | Auto-Revert |
|-------|------|--------------|-------------|
| **Full Test Suite** | Regression | `full-test-suite` | ⚠️ On P0/P1 failure |
| **Build Verification** | Artifact | `build-check` | ✅ Always |
| **Security Scan (SAST)** | Security | `security-scan` | ✅ Always |
| **Integration Tests** | E2E | `integration-tests` | ⚠️ On P0 failure |
| **Migration Validation** | Data | `migration-check` | ✅ Always |

**Auto-Revert Policy:**
- P0 failures (complete feature breakage, security vulnerability): Immediate auto-revert
- P1 failures (partial breakage): Manual investigation within 30 min, revert if no hotfix ETA
- P2 failures (performance degradation): Must fix in next PR, no immediate revert

### Warnings (Tracked)

| Check | Purpose | Resolution Deadline |
|-------|---------|---------------------|
| **Performance Regression** | Monitoring | 2 business days |
| **Documentation Drift** | Accuracy | Next release |
| **Flaky Test Detection** | Reliability | 1 sprint |

---

## Stage 3: Production Release Gates

**Workflow**: `.github/workflows/release.yml`  
**Trigger**: Manual workflow dispatch or automated tag creation  
**Purpose**: Final validation before production deployment

### Release Preconditions

Before a release can be created, ALL must be true:

- ✅ Main branch Quality Gates passing (last 3 consecutive builds green)
- ✅ No open P0/P1 issues targeting this release
- ✅ Release notes generated and approved
- ✅ Changelog updated with conventional commits
- ✅ Version bump follows SemVer rules
- ✅ Migration rollback plan documented (if DB changes)

### Hard Blocks (Must Pass)

| Check | Type | Workflow Job | Abort Release |
|-------|------|--------------|---------------|
| **Production Build** | Artifact | `production-build` | ✅ Always |
| **Docker Image Scan** | Security | `trivy-scan` | ✅ Always |
| **Smoke Tests (Staging)** | Pre-Prod Validation | `smoke-tests` | ✅ Always |
| **Dependency Audit** | Vulnerability | `npm-audit` | ⚠️ If HIGH+ severity |
| **Deployment Simulation** | Infrastructure | `deploy-dry-run` | ✅ Always |

**Release Blocking Criteria:**
- Any **CRITICAL or HIGH severity** vulnerability in production dependencies
- Smoke test failure rate >5%
- Deployment simulation error (infra, permissions, resource limits)

### Warnings (Document in Release Notes)

| Warning | Impact | Required Action |
|---------|--------|-----------------|
| **Performance Impact** | Latency increase >10% | Include mitigation plan |
| **Breaking Changes** | API/behavior change | Highlight in changelog |
| **Known Issues** | P2/P3 bugs | Document workarounds |

---

## Workflow-to-Gate Mapping

| Workflow | Gate Stage | Status Checks | Block Merge | Block Release |
|----------|-----------|---------------|-------------|---------------|
| **PR Validation** | Stage 1 | 7 checks | ✅ Yes | N/A |
| **Quality Gates** | Stage 2 | 5 checks | N/A[^3] | ✅ Indirectly |
| **Release** | Stage 3 | 5 checks | N/A | ✅ Yes |

[^3]: Quality Gates do not block merge (run post-merge), but failure triggers auto-revert policy which effectively removes the bad commit from `main`.

---

## Exception Process

### When to Request Exception

Exceptions to release gates are **rare and discouraged**. Valid scenarios:

| Scenario | Severity | Approval Required | Process |
|----------|----------|-------------------|---------|
| **Production Incident Hotfix** | P0/P1 | Engineering Lead + VP Eng | Use [Break-Glass Process](./EMERGENCY_BREAK_GLASS_RUNBOOK.md) |
| **Dependency License Review Pending** | P2 | Legal + Engineering Manager | Create tracking issue, set 7-day deadline |
| **External Type Stub Missing** | P3 | Maintainer | Document in PR, file upstream issue |
| **Flaky Test on Green Main** | P2 | CI Owner | Skip test with issue reference, fix in 24h |

### Exception Documentation Requirements

All exceptions must include:

1. **Tracking Issue**: Link to GitHub issue with exception justification
2. **Risk Assessment**: What could go wrong if bypassed
3. **Rollback Plan**: How to revert if exception causes problems
4. **Resolution Deadline**: When the bypass will be removed
5. **Audit Trail**: Logged in `governance/gate_exceptions.log`

---

## Compliance Monitoring

### KPIs

| Metric | Target | Review Frequency | Owner |
|--------|--------|------------------|-------|
| **PR Validation Pass Rate** | >95% | Weekly | CI Owner |
| **Main Revert Rate** | <2% per month | Weekly | Engineering Lead |
| **Release Failure Rate** | <1% per quarter | Monthly | Release Manager |
| **Gate Exception Rate** | <5 per month | Monthly | Engineering Manager |
| **Exception Resolution Time** | <7 days avg | Monthly | Engineering Manager |

### Audit Requirements

- Gate exception log reviewed **monthly** by Engineering Leadership
- Quarterly review of gate effectiveness (false positive rate, missed defects)
- Annual review of gate definitions and blocking criteria

---

## Escalation Path

If a gate is blocking legitimate work:

1. **Immediate**: Contact on-call CI Owner on Slack `#eng-ci`
2. **Within 1 hour**: Escalate to Engineering Manager if unresolved
3. **Within 4 hours**: Request exception via Break-Glass process if critical
4. **Retrospective**: File issue to improve gate if false positive pattern emerges

---

## Maintenance

| Action | Frequency | Owner | Next Review |
|--------|-----------|-------|-------------|
| **Update gate definitions** | Quarterly | Engineering Leadership | 2026-09-24 |
| **Review exception log** | Monthly | Engineering Manager | 2026-07-24 |
| **Audit KPIs** | Quarterly | CI Owner | 2026-09-24 |
| **Update workflow mapping** | On workflow changes | PR Author | N/A |

---

## Related Documentation

- [Branch Protection Policy (Coming Soon)](#) _(Issue #853)_
- [Status Check Matrix](./STATUS_CHECK_MATRIX.md)
- [Emergency Break-Glass Runbook](./EMERGENCY_BREAK_GLASS_RUNBOOK.md)
- [PR Review Policy (Coming Soon)](#) _(Issue #849)_

---

## Appendix: Sample Gate Failure Scenarios

### Scenario 1: Conventional Commit Failure
**Symptom**: PR check fails on `commitlint`  
**Cause**: Commit message does not follow `type(scope): message` format  
**Resolution**: Amend commit message or rebase to fix  
**Exception Allowed?**: ❌ Never  

### Scenario 2: Mypy Type Error on External Library
**Symptom**: PR check fails on `mypy-check` due to missing type stubs for third-party library  
**Cause**: Newly added dependency has no type hints  
**Resolution**:  
1. Create tracking issue for upstream type stub contribution  
2. Add `# type: ignore` with issue reference  
3. Document in PR description  
4. Maintainer approves as documented exception  

**Exception Allowed?**: ⚠️ Yes, with tracking issue + deadline  

### Scenario 3: Flaky Integration Test on Green Main
**Symptom**: Quality Gates fail on `integration-tests` despite passing locally  
**Cause**: Race condition in test, intermittent external service  
**Resolution**:  
1. Rerun test 2x to confirm flakiness  
2. If green on retry → allow merge, file issue to fix flake  
3. If red on retry → investigate, may be real regression  
4. Auto-revert triggered if P0 impact  

**Exception Allowed?**: ⚠️ Yes, if documented + fixed within 24h  

---

**Document Version**: 1.0  
**Approved By**: [Pending Review]  
**Effective Date**: [TBD]  
